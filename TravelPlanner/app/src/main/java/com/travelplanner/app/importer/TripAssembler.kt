package com.travelplanner.app.importer

import android.net.Uri
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Settings
import com.travelplanner.app.data.Stay
import com.travelplanner.app.data.TripDocument
import com.travelplanner.app.data.TripRepository
import com.travelplanner.app.reference.ReferenceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId

/** Итог обработки одного файла — для отчёта пользователю. */
data class FileResult(val name: String, val kind: Kind, val summary: String) {
    enum class Kind { FLIGHT, TRAIN, HOTEL, MIXED, DUPLICATE, UNKNOWN, ERROR }
}

/** Пункт, который распознан не полностью: пользователь дополнит его в редакторе. */
data class AttentionItem(
    val title: String,
    val missing: List<String>,
    val tripId: Long,
    val flight: ParsedFlight? = null,
    val train: ParsedTrain? = null,
    val stay: ParsedStay? = null,
    val attachment: String?,
    val booking: String?,
    val passenger: String?,
)

data class ImportReport(
    val files: List<FileResult>,
    /** Поездки, в которые что-то добавлено: id -> название. */
    val trips: List<Pair<Long, String>>,
    val added: Int,
    val duplicates: Int,
    val attention: List<AttentionItem>,
)

/**
 * Загрузка пачки файлов: распознаёт каждый (перелёт / поезд / отель), отбрасывает дубликаты,
 * раскладывает пункты по поездкам (по датам) и сохраняет — с календарём и напоминаниями.
 */
class TripAssembler(
    private val importer: TicketImporter,
    private val repo: TripRepository,
    private val ref: ReferenceData,
    private val settings: Settings,
) {
    private val grouping = TripGrouping(ref)

    suspend fun importAll(
        uris: List<Uri>,
        targetTripId: Long?,
        onProgress: (done: Int, total: Int, current: String) -> Unit,
    ): ImportReport = withContext(Dispatchers.IO) {
        val db = repo.db
        val results = mutableListOf<FileResult>()
        val items = mutableListOf<PlanItem>()
        val pendingStays = mutableListOf<Triple<ParsedStay, ImportResult, String?>>()
        val attentionDrafts = mutableListOf<AttentionItem>() // tripId заполним после группировки
        val docsByFile = linkedMapOf<String, Pair<String, String>>() // path -> (title, kind)
        val seenHashes = mutableSetOf<String>()
        val existingKeys = db.flights().all().map { it.dedupKey() }.toMutableSet()
        val existingStayKeys = db.stays().all().map { Converters.stayKey(it) }.toMutableSet()
        var duplicates = 0

        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size, uri.lastPathSegment ?: "файл")
            val r = runCatching { importer.import(uri) }.getOrElse {
                results += FileResult(uri.lastPathSegment ?: "файл", FileResult.Kind.ERROR, "Не удалось прочитать: ${it.message}")
                return@forEachIndexed
            }
            val name = r.displayName
            val path = r.storedFile.absolutePath
            if (!seenHashes.add(sha256(r.storedFile))) {
                r.storedFile.delete()
                duplicates++
                results += FileResult(name, FileResult.Kind.DUPLICATE, "Копия уже загруженного файла")
                return@forEachIndexed
            }
            val doc = r.document
            val parts = mutableListOf<String>()
            var newItems = 0

            doc.flights.forEach { p ->
                val f = Converters.flight(p, ref, 0, settings.remindHours * 60, path, doc.bookingRef, doc.passenger)
                val label = "✈ ${p.airlineCode} ${p.number} ${p.from ?: "?"}→${p.to ?: "?"}"
                when {
                    f == null -> attentionDrafts += AttentionItem(label, Converters.missing(p), 0, flight = p, attachment = path, booking = doc.bookingRef, passenger = doc.passenger)
                    !existingKeys.add(f.dedupKey()) -> duplicates++
                    else -> { items += PlanItem.Move(f, path); newItems++ }
                }
                parts += label
            }
            doc.trains.forEach { p ->
                val f = Converters.train(p, ref, 0, settings.trainRemindMinutes, path, doc.bookingRef, doc.passenger)
                val label = "🚆 Поезд ${p.number} ${p.from ?: "?"} → ${p.to ?: "?"}"
                when {
                    f == null -> attentionDrafts += AttentionItem(label, Converters.missing(p), 0, train = p, attachment = path, booking = doc.bookingRef, passenger = doc.passenger)
                    !existingKeys.add(f.dedupKey()) -> duplicates++
                    else -> { items += PlanItem.Move(f, path); newItems++ }
                }
                parts += label
            }
            doc.stay?.let { p ->
                pendingStays += Triple(p, r, doc.bookingRef)
                parts += "🏨 ${p.name ?: "Жильё"}"
            }
            val kind = when {
                doc.isEmpty -> FileResult.Kind.UNKNOWN
                listOf(doc.flights.isNotEmpty(), doc.trains.isNotEmpty(), doc.stay != null).count { it } > 1 -> FileResult.Kind.MIXED
                doc.trains.isNotEmpty() -> FileResult.Kind.TRAIN
                doc.stay != null -> FileResult.Kind.HOTEL
                else -> FileResult.Kind.FLIGHT
            }
            results += FileResult(
                name, kind,
                if (doc.isEmpty) "Не похоже на билет или бронь — сохранён в документы поездки" else parts.joinToString("\n"),
            )
            val docKind = when (kind) {
                FileResult.Kind.TRAIN -> "TRAIN"
                FileResult.Kind.HOTEL -> "HOTEL"
                FileResult.Kind.FLIGHT, FileResult.Kind.MIXED -> "FLIGHT"
                else -> "OTHER"
            }
            if (newItems > 0 || doc.isEmpty || doc.stay != null || attentionDrafts.any { it.attachment == path }) {
                docsByFile[path] = (parts.firstOrNull()?.let { "$it" } ?: name) to docKind
            } else {
                r.storedFile.delete() // все пункты файла уже есть в приложении
            }
        }
        onProgress(uris.size, uris.size, "")

        // Жильё: часовой пояс — по городу в адресе, иначе по прилёту накануне заселения
        val moves = items.filterIsInstance<PlanItem.Move>().map { it.flight }
        pendingStays.forEach { (p, r, booking) ->
            val path = r.storedFile.absolutePath
            val zone = ref.zoneForPlace(p.address.orEmpty() + " " + p.name.orEmpty()) ?: zoneByArrival(p, moves)
            val s = Converters.stay(p, zone, 0, path, booking)
            when {
                s == null -> attentionDrafts += AttentionItem("🏨 ${p.name ?: "Жильё"}", Converters.missing(p), 0, stay = p, attachment = path, booking = booking, passenger = null)
                !existingStayKeys.add(Converters.stayKey(s)) -> duplicates++
                else -> items += PlanItem.Hotel(s, path)
            }
        }

        // Группировка по поездкам
        val groups = if (targetTripId != null) listOf(items.toList()) else grouping.cluster(items)
        val affected = linkedMapOf<Long, String>()
        val tripOfFile = mutableMapOf<String, Long>()
        var added = 0
        for (group in groups) {
            if (group.isEmpty() && targetTripId == null) continue
            val tripId = targetTripId ?: findExistingTrip(group) ?: repo.createTrip(grouping.title(group))
            for (item in group) {
                when (item) {
                    is PlanItem.Move -> repo.saveFlight(item.flight.copy(tripId = tripId))
                    is PlanItem.Hotel -> repo.saveStay(item.stay.copy(tripId = tripId))
                }
                tripOfFile.putIfAbsent(item.file, tripId)
                added++
            }
            affected[tripId] = ""
        }
        val mainTrip = targetTripId ?: affected.keys.firstOrNull()
            ?: if (attentionDrafts.isNotEmpty() || docsByFile.isNotEmpty()) repo.createTrip("Новая поездка") else null
        mainTrip?.let { affected.putIfAbsent(it, "") }

        docsByFile.forEach { (path, info) ->
            val tripId = tripOfFile[path] ?: mainTrip ?: return@forEach
            repo.addDocument(TripDocument(tripId = tripId, title = info.first, path = path, kind = info.second))
        }
        val attention = attentionDrafts.map { it.copy(tripId = tripOfFile[it.attachment] ?: mainTrip ?: 0) }
        val trips = affected.keys.map { id -> id to (repo.db.trips().get(id)?.title ?: "Поездка") }
        ImportReport(results, trips, added, duplicates, attention)
    }

    /** Если даты пересекаются с уже существующей поездкой — добавляем в неё. */
    private suspend fun findExistingTrip(group: List<PlanItem>): Long? {
        val start = group.minOf { it.start }
        val end = group.maxOf { it.end }
        val db = repo.db
        val flights = db.flights().all().groupBy { it.tripId }
        val stays = db.stays().all().groupBy { it.tripId }
        return db.trips().all().firstOrNull { t ->
            val ranges = flights[t.id].orEmpty().map { it.departureMillis to (it.arrivalMillis ?: it.departureMillis) } +
                stays[t.id].orEmpty().map { it.checkInMillis to it.checkOutMillis }
            if (ranges.isEmpty()) return@firstOrNull false
            val tStart = ranges.minOf { it.first }
            val tEnd = ranges.maxOf { it.second }
            start <= tEnd + TripGrouping.GAP && end >= tStart - TripGrouping.GAP
        }?.id
    }

    private fun zoneByArrival(p: ParsedStay, moves: List<Flight>): ZoneId {
        val checkIn = p.checkIn.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val id = moves.filter { (it.arrivalMillis ?: it.departureMillis) <= checkIn }
            .maxByOrNull { it.departureMillis }?.arrivalZone
        return id?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
