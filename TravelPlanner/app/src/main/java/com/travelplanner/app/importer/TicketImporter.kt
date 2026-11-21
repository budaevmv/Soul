package com.travelplanner.app.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.travelplanner.app.reference.ReferenceData
import java.io.File
import java.time.LocalDate

data class ImportResult(
    val document: ParsedDocument,
    /** Имя файла, как его видит пользователь. */
    val displayName: String,
    /** Копия файла во внутреннем хранилище приложения (доступна офлайн). */
    val storedFile: File,
    val rawText: String,
)

class TicketImporter(private val context: Context, private val ref: ReferenceData) {

    private val extractor = DocumentTextExtractor(context)

    suspend fun import(uri: Uri): ImportResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val name = displayName(uri) ?: "документ"
        val stored = copyToDocs(uri, name)
        val content = extractor.extract(stored, mime)
        val doc = parse(content)
        return ImportResult(doc, name, stored, content.text)
    }

    fun parse(content: ExtractedContent, today: LocalDate = LocalDate.now()): ParsedDocument {
        val names = buildAirportNames()
        val parser = TicketParser(ref.airports.keys, ref.airlines.keys, names, today) { t -> ref.citiesIn(t).map { it.city } }
        val doc = parser.parse(content.text)
        val bcbp = content.barcodes.mapNotNull { BcbpParser.parse(it.trim(), today) }
        if (bcbp.isEmpty()) return doc
        return doc.copy(
            flights = BcbpParser.merge(doc.flights, bcbp.flatMap { it.flights }),
            bookingRef = doc.bookingRef ?: bcbp.firstNotNullOfOrNull { it.bookingRef },
            passenger = doc.passenger ?: bcbp.firstNotNullOfOrNull { it.passenger },
        )
    }

    /** Названия аэропортов и однозначные города на кириллице -> IATA. */
    private fun buildAirportNames(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ref.airports.values.forEach { a ->
            if (a.name.length >= 5 && a.name != a.city) result[a.name.uppercase()] = a.iata
        }
        ref.airports.values.groupBy { it.city }.forEach { (city, list) ->
            if (list.size == 1 && city.length >= 4) result[city.uppercase()] = list.first().iata
        }
        return result
    }

    /** Сохраняет вставленный текст брони как документ поездки. */
    fun saveText(text: String): File =
        File(docsDir(), "${System.currentTimeMillis()}_booking.txt").apply { writeText(text) }

    private fun docsDir(): File = File(context.filesDir, "docs").apply { mkdirs() }

    private fun copyToDocs(uri: Uri, name: String): File {
        val dir = docsDir()
        val safe = name.replace(Regex("[^\\w.\\-а-яА-ЯёЁ ]"), "_")
        val target = File(dir, "${System.currentTimeMillis()}_$safe")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("Не удалось открыть файл")
        return target
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}
