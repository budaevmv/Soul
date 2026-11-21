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

/** Поезд. Станции — как в билете (например «САРАТОВ 1 ПАСС»). */
data class ParsedTrain(
    val number: String,
    val from: String? = null,
    val to: String? = null,
    val depDate: LocalDate? = null,
    val depTime: LocalTime? = null,
    val arrDate: LocalDate? = null,
    val arrTime: LocalTime? = null,
    val car: String? = null,
    val seat: String? = null,
    /** Время в билете указано московское (иначе — местное). */
    val moscowTime: Boolean = false,
)

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
    val trains: List<ParsedTrain> = emptyList(),
) {
    val isEmpty: Boolean get() = flights.isEmpty() && stay == null && trains.isEmpty()
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
    /** Города, упомянутые в тексте, по порядку (для вокзалов). */
    private val cityFinder: (String) -> List<String> = { emptyList() },
) {

    private val nameRegexes: List<Pair<Regex, String>> = airportNames
        .filterKeys { it !in GENERIC_NAMES }
        .map { (name, code) -> Regex("(?<![А-ЯЁA-Z])" + Regex.escape(name) + "(?![А-ЯЁA-Z])") to code }

    fun parse(rawText: String): ParsedDocument {
        val text = normalize(rawText)
        val upper = text.uppercase()
        // ЖД-билет: в нём не ищем авиарейсы (номера вагонов/мест дают ложные совпадения)
        val trains = if (TRAIN_MARKER.containsMatchIn(upper) && CAR_MARKER.containsMatchIn(upper)) parseTrains(text, upper) else emptyList()
        val flights = if (trains.isEmpty()) parseFlights(upper) else emptyList()
        return ParsedDocument(
            flights = flights,
            stay = parseStay(text, upper, hasFlights = flights.isNotEmpty() || trains.isNotEmpty()),
            bookingRef = findBookingRef(upper),
            passenger = findPassenger(text),
            trains = trains,
        )
    }

    // ---------- Поезда ----------

    private fun parseTrains(text: String, upper: String): List<ParsedTrain> {
        data class Hit(val number: String, val start: Int, val end: Int)
        var hits = TRAIN_NO.findAll(upper).map { Hit(it.groupValues[1].replace(" ", ""), it.range.first, it.range.last + 1) }
            .distinctBy { it.number }.toList()
        if (hits.isEmpty()) {
            // Таблица «Поезд | Вагон | Место» и строкой ниже «009Ж 05 012»
            val marker = TRAIN_MARKER.find(upper) ?: return emptyList()
            val window = upper.substring(marker.range.first, minOf(upper.length, marker.range.first + 400))
            val m = TRAIN_NO_BARE.find(window) ?: return emptyList()
            hits = listOf(Hit(m.value, marker.range.first + m.range.first, marker.range.first + m.range.last + 1))
        }
        val moscow = MOSCOW_TIME.containsMatchIn(upper) && !LOCAL_TIME.containsMatchIn(upper)
        val yearHint = YEAR_HINT.find(upper)?.groupValues?.get(1)?.toInt()
        // В билетах РЖД дата часто без года («22.11»), год — отдельной строкой
        fun trainDates(r: IntRange): List<LocalDate> {
            if (r.isEmpty()) return emptyList()
            val w = upper.substring(r.first, r.last + 1)
            val found = datesWithPositions(w).toMutableList()
            for (m in DATE_DM.findAll(w)) {
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val date = yearHint?.let { date(it, mo, d) } ?: runCatching { inferYear(mo, d) }.getOrNull()
                date?.let { found += m.range.first to it }
            }
            return found.sortedBy { it.first }.map { it.second }
        }
        return hits.mapIndexed { i, hit ->
            val prevEnd = hits.getOrNull(i - 1)?.end ?: 0
            val next = hits.getOrNull(i + 1)?.start ?: minOf(upper.length, hit.end + 1500)
            // Дата/время и маршрут в билетах РЖД стоят выше строки «Поезд 717МА», иногда с большими отступами
            val range = maxOf(prevEnd, hit.start - 1500) until maxOf(hit.end, next)
            val window = upper.substring(range.first, range.last + 1)
            val original = text.substring(range.first, range.last + 1)

            fun afterLabel(label: Regex): Pair<LocalDate?, LocalTime?> {
                val m = label.find(window) ?: return null to null
                val r = (range.first + m.range.last + 1) until minOf(upper.length, range.first + m.range.last + 1 + 120)
                return trainDates(r).firstOrNull() to tripTimes(upper, r).firstOrNull()
            }
            val dates = trainDates(range)
            val times = tripTimes(upper, range)
            val (dl, tl) = afterLabel(TRAIN_DEP)
            val (da, ta) = afterLabel(TRAIN_ARR)
            val depDate = dl ?: dates.getOrNull(0)
            val depTime = tl ?: times.getOrNull(0)
            val arrTime = ta ?: times.getOrNull(1)
            var arrDate = da ?: dates.getOrNull(1)?.takeIf { depDate == null || !it.isBefore(depDate) } ?: depDate
            if (da == null && arrDate == depDate && depTime != null && arrTime != null && arrTime < depTime) arrDate = arrDate?.plusDays(1)

            val (from, to) = findRoute(original)
            ParsedTrain(
                number = hit.number,
                from = from,
                to = to,
                depDate = depDate,
                depTime = depTime,
                arrDate = if (arrTime != null) arrDate else null,
                arrTime = arrTime,
                car = CAR.find(window)?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { null },
                seat = TRAIN_SEAT.find(window)?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { null },
                moscowTime = moscow,
            )
        }
    }

    /** Время, кроме длительности («в пути 13:35»). */
    private fun tripTimes(upper: String, range: IntRange): List<LocalTime> {
        if (range.isEmpty()) return emptyList()
        return TIME.findAll(upper.substring(range.first, range.last + 1)).filter { m ->
            val abs = range.first + m.range.first
            !upper.substring(maxOf(0, abs - 20), abs).contains(Regex("ПУТИ|ПУТЬ|TRAVEL TIME|DURATION"))
        }.map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList()
    }

    /** Станции отправления и прибытия: «САРАТОВ 1 ПАСС — МОСКВА ПАВЕЛЕЦКАЯ», иначе — первые два города в тексте. */
    private fun findRoute(original: String): Pair<String?, String?> {
        val candidates = (ROUTE_UPPER.findAll(original) + ROUTE_MIXED.findAll(original)).map { m ->
            clean(m.groupValues[1]) to clean(m.groupValues[2])
        }.filter { (a, b) -> a.length >= 3 && b.length >= 3 && a != b }.toList()
        candidates.firstOrNull { (a, b) -> cityFinder(a).isNotEmpty() && cityFinder(b).isNotEmpty() }?.let { return it }
        val cities = cityFinder(original).distinct()
        if (cities.size >= 2) return cities[0] to cities[1]
        return candidates.firstOrNull() ?: (null to null)
    }

    private fun clean(station: String): String =
        station.trim().split(Regex("\\s+"))
            .takeWhile { it.uppercase() !in STATION_STOP_WORDS && !Regex("^\\d+[.:]|^\\d{2,}").containsMatchIn(it) }
            .joinToString(" ").trim(' ', '-', ',', '.')

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

        val depLabels = DEP_LABEL.findAll(upper).map { it.range }.toList()
        val arrLabels = ARR_LABEL.findAll(upper).map { it.range }.toList()
        val allLabels = (depLabels + arrLabels).sortedBy { it.first }

        return hits.mapIndexed { i, hit ->
            labeledFlight(upper, hits, i, depLabels, arrLabels, allLabels)?.let { return@mapIndexed it }
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

    /**
     * Квитанции с блоками «ВЫЛЕТ: … / ПРИЛЁТ: …» (DEPARTURE: / ARRIVAL:): дата, время и аэропорт
     * вылета часто стоят ДО номера рейса, поэтому берём их из своего блока.
     */
    private fun labeledFlight(
        upper: String,
        hits: List<FlightHit>,
        i: Int,
        depLabels: List<IntRange>,
        arrLabels: List<IntRange>,
        allLabels: List<IntRange>,
    ): ParsedFlight? {
        if (depLabels.isEmpty() || arrLabels.isEmpty()) return null
        val hit = hits[i]
        val prevEnd = hits.getOrNull(i - 1)?.end ?: 0
        val nextStart = hits.getOrNull(i + 1)?.start ?: upper.length
        val dep = depLabels.lastOrNull { it.first in prevEnd until hit.start }
            ?: depLabels.firstOrNull { it.first in hit.end until nextStart }
            ?: return null
        val arr = arrLabels.firstOrNull { it.first > maxOf(dep.last, hit.end) } ?: return null

        fun block(label: IntRange): IntRange {
            val end = allLabels.firstOrNull { it.first > label.last }?.first ?: minOf(upper.length, label.last + 300)
            return (label.last + 1) until minOf(end, label.last + 1 + 400)
        }
        val depBlock = block(dep)
        val arrBlock = block(arr)
        val from = findAirports(upper, depBlock).firstOrNull() ?: return null
        val to = findAirports(upper, arrBlock).firstOrNull { it != from } ?: return null
        val depDate = findDates(upper, depBlock).firstOrNull()
        val depTime = findTimes(upper, depBlock).firstOrNull()
        val arrDate = findDates(upper, arrBlock).firstOrNull()
        val arrTime = findTimes(upper, arrBlock).firstOrNull()
        val whole = upper.substring(dep.first, minOf(upper.length, arrBlock.last + 1))
        return ParsedFlight(
            airlineCode = hit.code,
            number = hit.number,
            from = from,
            to = to,
            depDate = depDate,
            depTime = depTime,
            arrDate = if (arrTime != null) (arrDate ?: depDate) else null,
            arrTime = arrTime,
            terminal = TERMINAL.find(upper.substring(depBlock.first, depBlock.last + 1))?.groupValues?.get(1),
            seat = SEAT.find(whole)?.groupValues?.get(1),
        )
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
        return datesWithPositions(upper.substring(range.first, range.last + 1)).map { it.second }
    }

    private fun datesWithPositions(window: String): List<Pair<Int, LocalDate>> {
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
        for (m in DATE_MONTH_FIRST.findAll(window)) {
            val month = MONTHS[m.groupValues[1].take(3)] ?: continue
            date(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())?.let { found += m.range.first to it }
        }
        return found.sortedBy { it.first }
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

        var inDate: LocalDate? = null
        var inTime: LocalTime? = null
        var outDate: LocalDate? = null
        var outTime: LocalTime? = null

        // Даты «в колонках»: строка «ноября ноября», а над ней числа «21 – 22» (возможно, вперемешку с другими колонками)
        columnDates(upper)?.let { (d1, d2, after) ->
            inDate = d1
            outDate = d2
            val times = findTimes(upper, after until minOf(upper.length, after + 150))
            inTime = times.getOrNull(0)
            outTime = times.getOrNull(1)
        }
        if (inDate == null) {
            val (d1, t1) = dateAfter(CHECK_IN)
            val (d2, t2) = dateAfter(CHECK_OUT)
            inDate = d1; inTime = t1; outDate = d2; outTime = t2
        }
        val checkIn = inDate ?: return null
        val address = lineAfter(text, ADDRESS)

        return ParsedStay(
            name = lineAfter(text, HOTEL_NAME) ?: nameBeforeAddress(text),
            address = address,
            checkIn = checkIn,
            checkInTime = inTime ?: LocalTime.of(14, 0),
            checkOut = outDate?.takeIf { !it.isBefore(checkIn) },
            checkOutTime = outTime ?: LocalTime.of(12, 0),
            phone = findPhone(text),
        )
    }

    private fun columnDates(upper: String): Triple<LocalDate, LocalDate, Int>? {
        val lines = upper.lines()
        var offset = 0
        for ((i, line) in lines.withIndex()) {
            val words = line.split(Regex("\\s+")).map { it.trim('.', ',') }.filter { it.isNotEmpty() }
            val pair = words.zipWithNext().firstOrNull { (a, b) -> a in MONTH_WORDS && b in MONTH_WORDS }
            if (pair != null) {
                // ближайшая строка сверху, где есть хотя бы два числа-дня
                val days = (i - 1 downTo maxOf(0, i - 3)).asSequence()
                    .map { j -> DAY_TOKEN.findAll(lines[j]).map { it.value.toInt() }.filter { it in 1..31 }.toList() }
                    .firstOrNull { it.size >= 2 }
                if (days != null) {
                    val m1 = MONTHS.getValue(pair.first.take(3))
                    val m2 = MONTHS.getValue(pair.second.take(3))
                    val d1 = inferYear(m1, days[0]) ?: return null
                    var d2 = inferYear(m2, days[1]) ?: return null
                    if (d2.isBefore(d1)) d2 = d2.plusYears(1)
                    return Triple(d1, d2, offset + line.length)
                }
            }
            offset += line.length + 1
        }
        return null
    }

    private fun lineAfter(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.get(1)?.trim()?.trim(':', ' ', '-')?.takeIf { it.length >= 3 }

    /** Название отеля часто стоит отдельной строкой над «Адрес отеля». */
    private fun nameBeforeAddress(text: String): String? {
        val m = ADDRESS.find(text) ?: return null
        return text.substring(0, m.range.first).lines().map { it.trim() }.lastOrNull { line ->
            line.length >= 3 && line.count { it.isLetter() } >= 3 && !TIME.containsMatchIn(line) &&
                !Regex("(?iu)^(ночь|ночи|ночей|nights?|с |до )").containsMatchIn(line)
        }
    }

    /** Телефон — первый номер в пределах пары строк после подписи (подпись и номер могут быть в разных колонках). */
    private fun findPhone(text: String): String? {
        PHONE.find(text)?.groupValues?.get(1)?.trim()?.let { return it }
        for (m in PHONE_KEYWORD.findAll(text)) {
            val window = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 1 + 200))
            PHONE_NUMBER.find(window)?.value?.trim()?.let { return it }
        }
        return null
    }

    /**
     * Код бронирования (PNR). В квитанциях он часто стоит не сразу за подписью, а в соседней
     * колонке таблицы, поэтому ищем похожий на PNR токен в окне после ключевых слов.
     * Приоритет: буквы+цифры (DRLC1L) > «несловарные» буквы (XKQLMN) > любой номер брони.
     */
    private fun findBookingRef(upper: String): String? {
        val windows = BOOKING_KEYWORD.findAll(upper).map { m ->
            upper.substring(m.range.last + 1, minOf(upper.length, m.range.last + 1 + 300))
        }.toList()
        for (w in windows) {
            PNR_TOKEN.findAll(w).map { it.value }.firstOrNull { t -> t.any(Char::isDigit) && t.any(Char::isLetter) }?.let { return it }
        }
        for (w in windows) {
            PNR_TOKEN.findAll(w).map { it.value }.firstOrNull { t -> t.all(Char::isLetter) && t.count { it in VOWELS } <= 1 }?.let { return it }
        }
        return BOOKING_REF.findAll(upper).map { it.groupValues[1] }.firstOrNull { t ->
            t.any(Char::isDigit) || t.count { it in VOWELS } <= 1
        } ?: BOOKING_NUMBER.find(upper)?.groupValues?.get(1)
            ?: ORDER_NUMBER.find(upper)?.groupValues?.get(1)
    }

    /** Имя пассажира — первая строка из 2–4 слов ЗАГЛАВНЫМИ буквами после подписи «Пассажир / ФИО». */
    private fun findPassenger(text: String): String? {
        // Имена в билетах почти всегда латиницей — сначала ищем латиницу, потом кириллицу
        for (regex in listOf(PASSENGER_NAME_LATIN, PASSENGER_NAME)) {
            for (m in PASSENGER_KEYWORD.findAll(text)) {
                val window = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 1 + 400))
                regex.findAll(window).map { it.value.trim() }
                    .firstOrNull { name -> name.split(' ', '/').filter { it.isNotBlank() }.none { it in NAME_STOP_WORDS } }
                    ?.let { return it }
            }
        }
        return null
    }

    private fun normalize(raw: String): String {
        var t = raw.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
        // Кириллические коды российских перевозчиков во внутренних билетах
        for ((cyr, lat) in CYRILLIC_CARRIERS) {
            t = Regex("(?<![А-ЯЁA-Z0-9])$cyr(?=\\s?-?\\s?\\d{2,4}(?!\\d))").replace(t, lat)
        }
        return t
    }

    companion object {
        private val FLIGHT_NO = Regex("(?<![A-Z0-9])([A-Z][A-Z0-9]|[0-9][A-Z])\\s?-?\\s?(\\d{2,4})(?![0-9:A-Za-zА-Яа-я])")
        private val FLIGHT_KEYWORD = Regex("FLIGHT|РЕЙС|FLT")
        private val IATA = Regex("(?<![A-Z])[A-Z]{3}(?![A-Z])")
        /** Названия аэропортов, совпадающие с обычными словами. */
        private val GENERIC_NAMES = setOf("НОВЫЙ", "ЦЕНТРАЛЬНЫЙ", "СОКОЛ", "БАЙКАЛ", "ФЕНИКС", "МАКЕДОНИЯ", "БОЛЬШОЕ САВИНО")
        private val AMBIGUOUS_WORDS = setOf("CAN", "BUS", "SIN", "OSS", "DEL", "BOM", "MAD", "VAR", "GOT", "CPT")
        private val TIME = Regex("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d])")
        private val DATE_NUMERIC = Regex("(?<!\\d)(\\d{1,2})[./](\\d{1,2})[./](\\d{4}|\\d{2})(?!\\d)")
        private val DATE_ISO = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")
        private val DATE_WORD = Regex("(?<!\\d)(\\d{1,2})\\s?([A-ZА-ЯЁ]{3,9})\\.?,?\\s?(\\d{4}|\\d{2}(?![:\\d]))?")
        private val DATE_MONTH_FIRST = Regex("(?<![A-ZА-ЯЁ])([A-Z]{3,9})\\.?\\s(\\d{1,2}),?\\s(\\d{4})(?!\\d)")
        private val DEP_LABEL = Regex("(?:ВЫЛЕТ|DEPARTURE|ОТПРАВЛЕНИЕ)\\s*:")
        private val ARR_LABEL = Regex("(?:ПРИЛ[ЕЁ]Т|ARRIVAL|ПРИБЫТИЕ)\\s*:")
        private val TERMINAL = Regex("(?:TERMINAL|ТЕРМИНАЛ)\\s*[:№]?\\s*([A-Z0-9А-Я]{1,2})(?![A-Z0-9А-Я])")
        private val SEAT = Regex("(?:SEAT|МЕСТО(?:\\s+НА\\s+БОРТУ)?)\\s*[:№]?\\s*(\\d{1,2}[A-K])(?![A-Z])")
        private val BOOKING_REF = Regex(
            "(?:BOOKING\\s*(?:REFERENCE|REF\\.?|CODE|NUMBER)?|PNR|CONFIRMATION\\s*(?:NUMBER|CODE)?|" +
                "КОД\\s*БРОНИ(?:РОВАНИЯ)?|НОМЕР\\s*БРОНИ(?:РОВАНИЯ)?|НОМЕР\\s*ЗАКАЗА|БРОНЬ)\\s*[:#№]?\\s*([A-ZА-Я0-9]{5,10})(?![A-ZА-Я0-9])",
        )
        private val BOOKING_KEYWORD = Regex(
            "BOOKING|PNR|CONFIRMATION|КОД\\s*БРОНИ|НОМЕР\\s*БРОНИ|ОНЛАЙН-РЕГИСТРАЦИ|CHECK-IN:|БРОНЬ",
        )
        private val PNR_TOKEN = Regex("(?<![A-ZА-ЯЁ0-9])[A-ZА-ЯЁ0-9]{6}(?![A-ZА-ЯЁ0-9])")
        private const val VOWELS = "AEIOUYАЕЁИОУЫЭЮЯ"
        private val PASSENGER_KEYWORD = Regex("(?iu)passenger(?![a-z])|пассажир(?:а|у|ы)?(?![а-яё])|фамилия,?\\s*имя|\\bФИО\\b|клиент")
        private val PASSENGER_NAME = Regex("(?<![A-Za-zА-Яа-яЁё])[A-ZА-ЯЁ]{2,}(?:[ /]{1,2}[A-ZА-ЯЁ]{2,}){1,3}(?![A-Za-zА-Яа-яЁё])")
        private val PASSENGER_NAME_LATIN = Regex("(?<![A-Za-zА-Яа-яЁё])[A-Z]{2,}(?:[ /]{1,2}[A-Z]{2,}){1,3}(?![A-Za-zА-Яа-яЁё])")
        private val NAME_STOP_WORDS = setOf(
            "ДАТА", "ЗАКАЗ", "ТЕЛЕФОН", "ПОКУПАТЕЛЯ", "ДОКУМЕНТ", "ПАСПОРТ", "НОМЕР", "БИЛЕТ", "БИЛЕТА", "ЭЛ", "ТРЕВЕЛ",
            "IATA", "PHONE", "CUSTOMER", "CONTACT", "TRAVEL", "DOCUMENT", "NO",
            "PASSPORT", "ORDER", "NUMBER", "TICKET", "BOOKING", "FLIGHT", "CLASS", "ECONOMY", "BUSINESS", "DATE",
            "ELECTRONIC", "ITINERARY", "RECEIPT", "SEAT", "FROM", "TO", "DEP", "ARR", "RUB", "USD", "EUR", "РФ", "ФИО",
        )
        private val STAY_KEYWORD = Regex("HOTEL|ОТЕЛ|ГОСТИНИЦ|ЗАЕЗД|CHECK-IN|CHECK IN|APARTMENT|АПАРТАМЕНТ|HOSTEL|ХОСТЕЛ|ПРОЖИВАНИ|ACCOMMODATION|AIRBNB|BOOKING\\.COM")
        private val STRONG_STAY_KEYWORD = Regex("HOTEL|ОТЕЛ|ГОСТИНИЦ|ЗАЕЗД|APARTMENT|АПАРТАМЕНТ|HOSTEL|ХОСТЕЛ|ПРОЖИВАНИ|ACCOMMODATION|AIRBNB")
        private val CHECK_IN = Regex("CHECK[- ]?IN|ЗАЕЗД|ДАТА ЗАЕЗДА|ARRIVAL")
        private val CHECK_OUT = Regex("CHECK[- ]?OUT|ВЫЕЗД|ОТЪЕЗД|ДАТА ВЫЕЗДА|DEPARTURE")
        private val DAY_TOKEN = Regex("(?<![\\d.,:№])\\d{1,2}(?![\\d.,:])")
        private val MONTH_WORDS: Set<String> = setOf(
            "ЯНВАРЬ", "ЯНВАРЯ", "ЯНВ", "ФЕВРАЛЬ", "ФЕВРАЛЯ", "ФЕВ", "МАРТ", "МАРТА", "МАР", "АПРЕЛЬ", "АПРЕЛЯ", "АПР",
            "МАЙ", "МАЯ", "ИЮНЬ", "ИЮНЯ", "ИЮН", "ИЮЛЬ", "ИЮЛЯ", "ИЮЛ", "АВГУСТ", "АВГУСТА", "АВГ",
            "СЕНТЯБРЬ", "СЕНТЯБРЯ", "СЕН", "СЕНТ", "ОКТЯБРЬ", "ОКТЯБРЯ", "ОКТ", "НОЯБРЬ", "НОЯБРЯ", "НОЯ", "НОЯБ",
            "ДЕКАБРЬ", "ДЕКАБРЯ", "ДЕК",
            "JANUARY", "JAN", "FEBRUARY", "FEB", "MARCH", "MAR", "APRIL", "APR", "MAY", "JUNE", "JUN", "JULY", "JUL",
            "AUGUST", "AUG", "SEPTEMBER", "SEP", "SEPT", "OCTOBER", "OCT", "NOVEMBER", "NOV", "DECEMBER", "DEC",
        )
        private val TRAIN_MARKER = Regex("ПОЕЗД|TRAIN")
        private val CAR_MARKER = Regex("ВАГОН|\\bCAR\\b|COACH")
        private val TRAIN_NO = Regex(
            "(?:№\\s*ПОЕЗДА|ПОЕЗД(?:А)?(?:\\s*/\\s*TRAIN)?|TRAIN)\\s*(?:№|N|НОМЕР|NO\\.?)?\\s*:?\\s*(\\d{1,4}\\s?[А-ЯЁA-Z]{1,2})(?![А-ЯЁA-Z0-9])",
        )
        private val TRAIN_NO_BARE = Regex("(?<![\\dА-ЯЁA-Z])\\d{3}[А-ЯЁ]{1,2}(?![А-ЯЁA-Z0-9])")
        private val TRAIN_DEP = Regex("ОТПРАВЛЕНИ[ЕЯ]|ОТПР\\.|DEPARTURE")
        private val TRAIN_ARR = Regex("ПРИБЫТИ[ЕЯ]|ПРИБ\\.|ARRIVAL")
        private val CAR = Regex("ВАГОН\\s*(?:№|N)?\\s*:?\\s*(\\d{1,2})(?!\\d)")
        private val TRAIN_SEAT = Regex("МЕСТ[ОА]\\s*(?:№|N)?\\s*:?\\s*(\\d{1,3}[А-ЯЁA-Z]?)(?![\\dА-ЯЁA-Z])")
        private val MOSCOW_TIME = Regex("МСК|МОСКОВСК")
        private val LOCAL_TIME = Regex("МЕСТН")
        private val YEAR_HINT = Regex("ГОД СОВЕРШЕНИЯ ПОЕЗДКИ\\s*:?\\s*(\\d{4})")
        private val DATE_DM = Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})(?![\\d.])")
        private const val STATION = "[А-ЯЁ][А-ЯЁ0-9.\\-]*(?: [А-ЯЁ0-9.\\-]+){0,3}"
        private val ROUTE_UPPER = Regex("($STATION)\\s*(?:\\(\\d{5,8}\\))?\\s*(?:—|→|–| - |->)\\s*($STATION)")
        private val ROUTE_MIXED = Regex(
            "([А-ЯЁ][а-яё]+(?:[ -][А-ЯЁа-яё0-9]+){0,3})\\s*(?:\\(\\d{5,8}\\))?\\s*(?:—|→|–| - |->)\\s*([А-ЯЁ][а-яё]+(?:[ -][А-ЯЁа-яё0-9]+){0,3})",
        )
        private val STATION_STOP_WORDS = setOf(
            "ОТПРАВЛЕНИЕ", "ПРИБЫТИЕ", "ПОЕЗД", "ВАГОН", "МЕСТО", "ДАТА", "ВРЕМЯ", "ОТПР", "ПРИБ", "ФИО", "ПАССАЖИР", "НОМЕР",
        )
        private val ORDER_NUMBER = Regex("(?:ЗАКАЗ[А]?|ЭЛЕКТРОННЫЙ БИЛЕТ|БИЛЕТ)\\s*(?:№|N)?\\s*:?\\s*(\\d{8,16})(?!\\d)")
        private val PHONE_KEYWORD = Regex("(?iu)телефон|phone|тел\\.")
        private val PHONE_NUMBER = Regex("\\+?\\d[\\d ()\\-]{8,18}\\d")
        private val BOOKING_NUMBER = Regex("(?:БРОНИРОВАНИ[ЕЯ]|BOOKING|ЗАКАЗ)[^№#\\n]{0,40}\\s*[№#]\\s*([A-ZА-Я0-9\\-]{5,20})")
        private val HOTEL_NAME = Regex("(?iu)(?:hotel name|hotel|property|отель|гостиница|объект размещения|объект)\\s*[:]\\s*([^\\n]+)")
        private val ADDRESS = Regex("(?iu)(?:address|адрес(?:\\s+отеля|\\s+объекта)?)[ \\t]*:?[ \\t]*(?:\\n\\s*)?([^\\n]+)")
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
