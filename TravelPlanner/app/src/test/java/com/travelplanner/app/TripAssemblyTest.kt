package com.travelplanner.app

import com.travelplanner.app.importer.Converters
import com.travelplanner.app.importer.ParsedDocument
import com.travelplanner.app.importer.PlanItem
import com.travelplanner.app.importer.TicketParser
import com.travelplanner.app.importer.TripGrouping
import com.travelplanner.app.reference.ReferenceData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Сквозная проверка: пачка документов -> пункты -> одна поездка с маршрутом и названием. */
class TripAssemblyTest {
    private val ref: ReferenceData = run {
        val dir = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.exists() }
        ReferenceData.parse(File(dir, "airports.json").readText(), File(dir, "airlines.json").readText(), File(dir, "countries.json").readText())
    }
    private val today = LocalDate.of(2026, 11, 20)

    private fun parser(): TicketParser {
        val names = mutableMapOf<String, String>()
        ref.airports.values.forEach { if (it.name.length >= 5 && it.name != it.city) names[it.name.uppercase()] = it.iata }
        ref.airports.values.groupBy { it.city }.forEach { (city, l) -> if (l.size == 1 && city.length >= 4) names[city.uppercase()] = l.first().iata }
        return TicketParser(ref.airports.keys, ref.airlines.keys, names, today) { t -> ref.citiesIn(t).map { it.city } }
    }

    /** Повторяет логику TripAssembler без Android: распознать, убрать дубликаты, собрать. */
    private fun assemble(docs: List<ParsedDocument>): Pair<List<List<PlanItem>>, Int> {
        val items = mutableListOf<PlanItem>()
        val keys = mutableSetOf<String>()
        var dup = 0
        docs.forEachIndexed { i, d ->
            d.flights.forEach { p -> Converters.flight(p, ref, 0, 180, "f$i", d.bookingRef, d.passenger)?.let { if (keys.add(it.dedupKey())) items += PlanItem.Move(it, "f$i") else dup++ } }
            d.trains.forEach { p -> Converters.train(p, ref, 0, 90, "f$i", d.bookingRef, d.passenger)?.let { if (keys.add(it.dedupKey())) items += PlanItem.Move(it, "f$i") else dup++ } }
        }
        val moves = items.filterIsInstance<PlanItem.Move>().map { it.flight }
        docs.forEachIndexed { i, d ->
            d.stay?.let { p ->
                val zone = ref.zoneForPlace(p.address.orEmpty()) ?: ZoneId.of(moves.first().arrivalZone)
                Converters.stay(p, zone, 0, "f$i", d.bookingRef)?.let { s ->
                    if (keys.add(Converters.stayKey(s))) items += PlanItem.Hotel(s, "f$i") else dup++
                }
            }
        }
        return TripGrouping(ref).cluster(items) to dup
    }

    @Test
    fun documentsOfAllTypesBecomeOneTrip() {
        val p = parser()
        // Квитанция «Победы» загружена дважды — как у пользователя в «Проводнике»
        val docs = listOf(Samples.POBEDA, Samples.HOTEL, Samples.RZD, Samples.AGENCY, Samples.POBEDA).map { p.parse(it) }
        val (groups, dup) = assemble(docs)
        assertEquals(1, groups.size)
        assertEquals(1, dup)
        val trip = groups.single()
        val route = trip.sortedBy { it.start }.map {
            when (it) {
                is PlanItem.Move -> if (it.flight.isTrain) "🚆${it.flight.fromName}→${it.flight.toName}" else "✈${it.flight.fromIata}→${it.flight.toIata}"
                is PlanItem.Hotel -> "🏨${it.stay.name}"
            }
        }
        // По времени: заселение 21.11 14:00 раньше рейса 21.11 22:30
        assertEquals(listOf("🏨Test Hills Hotel", "✈GSV→SVO", "🚆Москва Белорусская→Минск", "✈MSQ→DXB", "✈DXB→KUL"), route)
        assertEquals("Куала-Лумпур, ноябрь 2026", TripGrouping(ref).title(trip, ZoneId.of("Europe/Moscow")))

        // Время поезда — местное: Москва и Минск обе UTC+3, в пути 7 ч 06 мин
        val train = trip.filterIsInstance<PlanItem.Move>().first { it.flight.isTrain }.flight
        assertEquals(7 * 60 + 6L, (train.arrivalMillis!! - train.departureMillis) / 60_000)
        assertEquals("Europe/Minsk", train.arrivalZone)
        // Саратов UTC+4 -> Москва UTC+3: 22:30 -> 23:20 = 1 ч 50 мин
        val pobeda = trip.filterIsInstance<PlanItem.Move>().first { it.flight.fromIata == "GSV" }.flight
        assertEquals(110L, (pobeda.arrivalMillis!! - pobeda.departureMillis) / 60_000)
        // Отель в Москве — московское время
        assertEquals("Europe/Moscow", trip.filterIsInstance<PlanItem.Hotel>().single().stay.zone)
    }

    @Test
    fun farApartDocumentsBecomeSeparateTrips() {
        val p = parser()
        // Поезд в ноябре и перелёт в марте — две разные поездки
        val docs = listOf(Samples.RZD, "Flight SU 1124 SVO - AER 15.03.2027 07:35 10:05").map { p.parse(it) }
        val (groups, _) = assemble(docs)
        assertEquals(2, groups.size)
    }

    /**
     * Полная сборка из папки с настоящими PDF: ./gradlew testDebugUnitTest -Ptickets.dir=/папка
     * Без параметра пропускается.
     */
    @Test
    fun realFolder() {
        val dir = System.getProperty("tickets.dir")
        org.junit.Assume.assumeTrue(!dir.isNullOrBlank())
        val p = parser()
        val files = File(dir).listFiles { f -> f.extension.equals("pdf", true) }!!.sortedBy { it.name }
        val seen = mutableSetOf<String>()
        val docs = files.mapNotNull { f ->
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            if (!seen.add(hash)) { println("FILE ${f.name}: дубликат файла"); return@mapNotNull null }
            val text = org.apache.pdfbox.pdmodel.PDDocument.load(f).use { d ->
                org.apache.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(d)
            }
            p.parse(text).also { println("FILE ${f.name}: $it") }
        }
        val (groups, dup) = assemble(docs)
        println("DUPLICATE ITEMS: $dup")
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
        fun t(ms: Long, z: String) = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.of(z)).format(fmt)
        groups.forEach { g ->
            println("PLAN:\n" + timeline(g).joinToString("\n") { "  " + describe(it) })
            println("TRIP: ${TripGrouping(ref).title(g, ZoneId.of("Europe/Moscow"))}")
            g.sortedBy { it.start }.forEach {
                when (it) {
                    is PlanItem.Move -> with(it.flight) {
                        println("  ${if (isTrain) "TRAIN $flightNumber $fromName → $toName" else "FLIGHT $airlineCode $flightNumber $fromIata → $toIata"}" +
                            "  ${t(departureMillis, departureZone)} → ${arrivalMillis?.let { a -> t(a, arrivalZone) }}  term=$departureTerminal seat=$seat car=$car ref=$bookingRef")
                    }
                    is PlanItem.Hotel -> with(it.stay) {
                        println("  HOTEL $name | $address | ${t(checkInMillis, zone)} → ${t(checkOutMillis, zone)} | $phone | $bookingRef")
                    }
                }
            }
        }
    }

    /** План по дням из собранной поездки (id как после сохранения в базу). */
    private fun timeline(trip: List<PlanItem>): List<com.travelplanner.app.ui.TimelineEntry> {
        var id = 1L
        val flights = trip.filterIsInstance<PlanItem.Move>().map { it.flight.copy(id = id++, tripId = 1) }
        val stays = trip.filterIsInstance<PlanItem.Hotel>().map { it.stay.copy(id = id++, tripId = 1) }
        return com.travelplanner.app.ui.Timeline.build(flights, stays, ref)
    }

    private fun describe(e: com.travelplanner.app.ui.TimelineEntry): String {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
        val time = java.time.Instant.ofEpochMilli(e.at).atZone(ZoneId.of(e.zone)).format(fmt)
        return time + " " + when (e) {
            is com.travelplanner.app.ui.TimelineEntry.GoToAirport -> "ВЫЕЗД ${e.flight.flightNumber}"
            is com.travelplanner.app.ui.TimelineEntry.Layover -> "ПЕРЕСАДКА ${e.flight.fromIata} -> ${e.flight.flightNumber}"
            is com.travelplanner.app.ui.TimelineEntry.FlightEntry -> "РЕЙС ${e.flight.flightNumber}"
            is com.travelplanner.app.ui.TimelineEntry.Transfer -> "В ОТЕЛЬ ${e.stay.name} после ${e.flight.flightNumber}"
            is com.travelplanner.app.ui.TimelineEntry.CheckIn -> "ЗАСЕЛЕНИЕ ${e.stay.name}"
            is com.travelplanner.app.ui.TimelineEntry.CheckOut -> "ВЫЕЗД ИЗ ${e.stay.name}"
        }
    }

    @Test
    fun timelineHandlesConnectionsAndHotelTransfers() {
        val p = parser()
        val (groups, _) = assemble(listOf(Samples.POBEDA, Samples.HOTEL, Samples.RZD, Samples.AGENCY).map { p.parse(it) })
        val plan = timeline(groups.single()).map { describe(it) }
        println(plan.joinToString("\n"))
        // Дубай — пересадка: без «выезда в аэропорт»
        org.junit.Assert.assertTrue(plan.any { it.contains("ПЕРЕСАДКА DXB -> 342") })
        org.junit.Assert.assertTrue(plan.none { it.contains("ВЫЕЗД 342") })
        // В Минске пересадка с поезда на самолёт: в аэропорт ехать нужно
        org.junit.Assert.assertTrue(plan.any { it.contains("ВЫЕЗД 1716") })
        // В отель (Москва) — только после прилёта в Москву, заселение сразу после прилёта
        assertEquals(listOf("В ОТЕЛЬ Test Hills Hotel после 6864"), plan.filter { it.contains("В ОТЕЛЬ") }.map { it.substringAfter(" ").substringAfter(" ") })
        org.junit.Assert.assertTrue(plan.any { it.startsWith("21.11 23:20 ЗАСЕЛЕНИЕ") })
    }
}
