package com.travelplanner.app.importer

import java.time.LocalDate
import java.time.LocalTime

data class ParsedFlight(
    val airlineCode: String,
    val number: String,
    val from: String? = null,
    val to: String? = null,
    val depDate: LocalDate? = null,
    val depTime: LocalTime? = null,
    val arrDate: LocalDate? = null,
    val arrTime: LocalTime? = null,
    val terminal: String? = null,
    val seat: String? = null,
) {
    val key: String get() = airlineCode + number
}

data class ParsedStay(
    val name: String?,
    val address: String?,
    val checkIn: LocalDate,
    val checkInTime: LocalTime,
    val checkOut: LocalDate?,
    val checkOutTime: LocalTime,
    val phone: String?,
)

data class ParsedDocument(
    val flights: List<ParsedFlight>,
    val stay: ParsedStay?,
    val bookingRef: String?,
    val passenger: String?,
) {
    val isEmpty: Boolean get() = flights.isEmpty() && stay == null
}

/**
 * Эвристический разбор текста маршрутной квитанции / посадочного / брони отеля.
 * Ищет номера рейсов, коды аэропортов IATA, даты, время, код бронирования, терминал и место.
 */
class TicketParser(
    private val airportCodes: Set<String>,
    private val airlineCodes: Set<String>,
    /** Названия аэропортов/городов (в верхнем регистре) -> IATA, для билетов без кодов. */
    private val airportNames: Map<String, String> = emptyMap(),
    private val today: LocalDate = LocalDate.now(),
) {

    private val nameRegexes: List<Pair<Regex, String>> = airportNames
        .filterKeys { it !in GENERIC_NAMES }
        .map { (name, code) -> Regex("(?<![А-ЯЁA-Z])" + Regex.escape(name) + "(?![А-ЯЁA-Z])") to code }

    fun parse(rawText: String): ParsedDocument {
        val text = normalize(rawText)
        val upper = text.uppercase()
        val flights = parseFlights(upper)
        return ParsedDocument(
            flights = flights,
            stay = parseStay(text, upper, hasFlights = flights.isNotEmpty()),
            bookingRef = BOOKING_REF.find(upper)?.groupValues?.get(1),
            passenger = findPassenger(text),
        )
    }

    // ---------- Рейсы ----------

    private data class FlightHit(val code: String, val number: String, val start: Int, val end: Int)

    private fun parseFlights(upper: String): List<ParsedFlight> {
        val hits = mutableListOf<FlightHit>()
        val seen = mutableSetOf<String>()
        for (m in FLIGHT_NO.findAll(upper)) {
            val code = m.groupValues[1]
            val number = m.groupValues[2].trimStart('0').ifEmpty { "0" }
            val before = upper.substring(maxOf(0, m.range.first - 25), m.range.first)
            val known = code in airlineCodes
            val hasKeyword = FLIGHT_KEYWORD.containsMatchIn(before)
            if (!known && !hasKeyword) continue
            if (!code.any { it.isLetter() }) continue
            if (seen.add(code + number)) hits += FlightHit(code, number, m.range.first, m.range.last + 1)
        }

        return hits.mapIndexed { i, hit ->
            val nextStart = hits.getOrNull(i + 1)?.start ?: minOf(upper.length, hit.end + 500)
            val prevEnd = hits.getOrNull(i - 1)?.end ?: 0
            val forward = hit.end until maxOf(hit.end, nextStart)
            val wide = maxOf(prevEnd, hit.start - 300) until maxOf(hit.end, nextStart)

            val airports = findAirports(upper, forward).takeIf { it.size >= 2 } ?: findAirports(upper, wide)
            val dates = findDates(upper, forward).ifEmpty { findDates(upper, wide) }
            val times = findTimes(upper, forward).ifEmpty { findTimes(upper, wide) }
            val segment = upper.substring(forward.first, forward.last + 1)

            val depDate = dates.getOrNull(0)
            val depTime = times.getOrNull(0)
            val arrTime = times.getOrNull(1)
            var arrDate = dates.getOrNull(1)?.takeIf { depDate == null || !it.isBefore(depDate) } ?: depDate
            if (arrDate != null && arrDate == depDate && depTime != null && arrTime != null && arrTime < depTime) {
                arrDate = arrDate.plusDays(1)
            }
            ParsedFlight(
                airlineCode = hit.code,
                number = hit.number,
                from = airports.getOrNull(0),
                to = airports.getOrNull(1),
                depDate = depDate,
                depTime = depTime,
                arrDate = if (arrTime != null) arrDate else null,
                arrTime = arrTime,
                terminal = TERMINAL.find(segment)?.groupValues?.get(1),
                seat = SEAT.find(segment)?.groupValues?.get(1),
            )
        }
    }

    private fun findAirports(upper: String, range: IntRange): List<String> {
        if (range.isEmpty()) return emptyList()
        val window = upper.substring(range.first, range.last + 1)
        val found = mutableListOf<Pair<Int, String>>()
        for (m in IATA.findAll(window)) {
            val code = m.value
            if (code !in airportCodes) continue
            val inParens = m.range.first > 0 && window[m.range.first - 1] == '('
            if (!inParens && code in AMBIGUOUS_WORDS) continue
            found += m.range.first to code
        }
        for ((regex, code) in nameRegexes) {
            regex.findAll(window).forEach { found += it.range.first to code }
        }
        val ordered = found.sortedBy { it.first }.map { it.second }
        // убираем подряд идущие повторы (например «Шереметьево (SVO)»)
        return ordered.fold(mutableListOf<String>()) { acc, code ->
            if (acc.lastOrNull() != code) acc += code
            acc
        }.distinct()
    }

    // ---------- Даты и время ----------

    fun findDates(upper: String, range: IntRange = upper.indices): List<LocalDate> {
        if (range.isEmpty()) return emptyList()
        val window = upper.substring(range.first, range.last + 1)
        val found = mutableListOf<Pair<Int, LocalDate>>()
        for (m in DATE_NUMERIC.findAll(window)) {
            val (d, mo, y) = m.destructured
            date(fullYear(y), mo.toInt(), d.toInt())?.let { found += m.range.first to it }
        }
        for (m in DATE_ISO.findAll(window)) {
            val (y, mo, d) = m.destructured
            date(y.toInt(), mo.toInt(), d.toInt())?.let { found += m.range.first to it }
        }
        for (m in DATE_WORD.findAll(window)) {
            val day = m.groupValues[1].toInt()
            val month = MONTHS[m.groupValues[2].take(3)] ?: continue
            val y = m.groupValues[3]
            val d = if (y.isNotEmpty()) date(fullYear(y), month, day) else inferYear(month, day)
            d?.let { found += m.range.first to it }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun findTimes(upper: String, range: IntRange): List<LocalTime> {
        if (range.isEmpty()) return emptyList()
        val window = upper.substring(range.first, range.last + 1)
        return TIME.findAll(window).map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList()
    }

    private fun fullYear(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun date(y: Int, m: Int, d: Int): LocalDate? =
        if (y in 2000..2100) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null

    /** Дата без года: берём ближайшую будущую (с запасом в месяц назад). */
    private fun inferYear(month: Int, day: Int): LocalDate? {
        val thisYear = date(today.year, month, day) ?: return null
        return if (thisYear.isBefore(today.minusDays(30))) thisYear.plusYears(1) else thisYear
    }

    // ---------- Проживание ----------

    private fun parseStay(text: String, upper: String, hasFlights: Boolean): ParsedStay? {
        if (!STAY_KEYWORD.containsMatchIn(upper)) return null
        if (hasFlights && !STRONG_STAY_KEYWORD.containsMatchIn(upper)) return null

        fun dateAfter(regex: Regex): Pair<LocalDate?, LocalTime?> {
            val m = regex.find(upper) ?: return null to null
            val end = minOf(upper.length, m.range.last + 120)
            val range = (m.range.last + 1) until end
            val d = findDates(upper, range).firstOrNull()
            val t = findTimes(upper, range).firstOrNull()
            return d to t
        }

        val (inDate, inTime) = dateAfter(CHECK_IN)
        val (outDate, outTime) = dateAfter(CHECK_OUT)
        val checkIn = inDate ?: return null

        return ParsedStay(
            name = lineAfter(text, HOTEL_NAME),
            address = lineAfter(text, ADDRESS),
            checkIn = checkIn,
            checkInTime = inTime ?: LocalTime.of(14, 0),
            checkOut = outDate?.takeIf { !it.isBefore(checkIn) },
            checkOutTime = outTime ?: LocalTime.of(12, 0),
            phone = PHONE.find(text)?.groupValues?.get(1)?.trim(),
        )
    }

    private fun lineAfter(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.get(1)?.trim()?.trim(':', ' ', '-')?.takeIf { it.length >= 3 }

    private fun findPassenger(text: String): String? =
        PASSENGER.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 4 }

    private fun normalize(raw: String): String {
        var t = raw.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
        // Кириллические коды российских перевозчиков во внутренних билетах
        for ((cyr, lat) in CYRILLIC_CARRIERS) {
            t = Regex("(?<![А-ЯЁA-Z0-9])$cyr(?=\\s?-?\\s?\\d{2,4}(?!\\d))").replace(t, lat)
        }
        return t
    }

    companion object {
        private val FLIGHT_NO = Regex("(?<![A-Z0-9])([A-Z][A-Z0-9]|[0-9][A-Z])\\s?-?\\s?(\\d{2,4})(?![0-9:])")
        private val FLIGHT_KEYWORD = Regex("FLIGHT|РЕЙС|FLT")
        private val IATA = Regex("(?<![A-Z])[A-Z]{3}(?![A-Z])")
        /** Названия аэропортов, совпадающие с обычными словами. */
        private val GENERIC_NAMES = setOf("НОВЫЙ", "ЦЕНТРАЛЬНЫЙ", "СОКОЛ", "БАЙКАЛ", "ФЕНИКС", "МАКЕДОНИЯ", "БОЛЬШОЕ САВИНО")
        private val AMBIGUOUS_WORDS = setOf("CAN", "BUS", "SIN", "OSS", "DEL", "BOM", "MAD", "VAR", "GOT", "CPT")
        private val TIME = Regex("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d])")
        private val DATE_NUMERIC = Regex("(?<!\\d)(\\d{1,2})[./](\\d{1,2})[./](\\d{4}|\\d{2})(?!\\d)")
        private val DATE_ISO = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")
        private val DATE_WORD = Regex("(?<!\\d)(\\d{1,2})\\s?([A-ZА-ЯЁ]{3,9})\\.?,?\\s?(\\d{4}|\\d{2}(?![:\\d]))?")
        private val TERMINAL = Regex("(?:TERMINAL|ТЕРМИНАЛ)\\s*[:№]?\\s*([A-Z0-9А-Я]{1,2})(?![A-Z0-9А-Я])")
        private val SEAT = Regex("(?:SEAT|МЕСТО)\\s*[:№]?\\s*(\\d{1,2}[A-K])(?![A-Z])")
        private val BOOKING_REF = Regex(
            "(?:BOOKING\\s*(?:REFERENCE|REF\\.?|CODE|NUMBER)?|PNR|CONFIRMATION\\s*(?:NUMBER|CODE)?|" +
                "КОД\\s*БРОНИ(?:РОВАНИЯ)?|НОМЕР\\s*БРОНИ(?:РОВАНИЯ)?|НОМЕР\\s*ЗАКАЗА|БРОНЬ)\\s*[:#№]?\\s*([A-ZА-Я0-9]{5,10})(?![A-ZА-Я0-9])",
        )
        private val PASSENGER = Regex(
            "(?iu)(?:passenger(?:\\s*name)?|пассажир|фамилия,?\\s*имя|name of passenger)\\s*[:]?\\s*([A-ZА-ЯЁ][A-ZА-ЯЁa-zа-яё/ .\\-]{3,40})",
        )
        private val STAY_KEYWORD = Regex("HOTEL|ОТЕЛ|ГОСТИНИЦ|ЗАЕЗД|CHECK-IN|CHECK IN|APARTMENT|АПАРТАМЕНТ|HOSTEL|ХОСТЕЛ|ПРОЖИВАНИ|ACCOMMODATION|AIRBNB|BOOKING\\.COM")
        private val STRONG_STAY_KEYWORD = Regex("HOTEL|ОТЕЛ|ГОСТИНИЦ|ЗАЕЗД|APARTMENT|АПАРТАМЕНТ|HOSTEL|ХОСТЕЛ|ПРОЖИВАНИ|ACCOMMODATION|AIRBNB")
        private val CHECK_IN = Regex("CHECK[- ]?IN|ЗАЕЗД|ДАТА ЗАЕЗДА|ARRIVAL")
        private val CHECK_OUT = Regex("CHECK[- ]?OUT|ВЫЕЗД|ДАТА ВЫЕЗДА|DEPARTURE")
        private val HOTEL_NAME = Regex("(?iu)(?:hotel name|hotel|property|отель|гостиница|объект размещения|объект)\\s*[:]\\s*([^\\n]+)")
        private val ADDRESS = Regex("(?iu)(?:address|адрес)\\s*[:]?\\s*([^\\n]+)")
        private val PHONE = Regex("(?iu)(?:phone|tel\\.?|телефон|тел\\.)\\s*[:]?\\s*(\\+?[\\d][\\d\\s()\\-]{6,20}\\d)")

        private val CYRILLIC_CARRIERS = listOf(
            "СУ" to "SU", "ДП" to "DP", "С7" to "S7", "У6" to "U6", "ЮТ" to "UT", "ФВ" to "FV", "5Н" to "5N", "ВЖ" to "WZ", "ЮЖ" to "ZF",
        )

        private val MONTHS = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
            "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
            "ЯНВ" to 1, "ФЕВ" to 2, "МАР" to 3, "АПР" to 4, "МАЙ" to 5, "МАЯ" to 5, "ИЮН" to 6,
            "ИЮЛ" to 7, "АВГ" to 8, "СЕН" to 9, "ОКТ" to 10, "НОЯ" to 11, "ДЕК" to 12,
        )
    }
}
