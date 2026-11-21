package com.travelplanner.app.importer

import java.time.LocalDate
import kotlin.math.abs

/**
 * Разбор штрихкода посадочного талона в стандарте IATA BCBP (Resolution 792).
 * Пример: "M1IVANOV/IVAN         EABC123 SVOISTSU 2130 325Y012A0045 100"
 */
object BcbpParser {

    data class Result(val flights: List<ParsedFlight>, val passenger: String?, val bookingRef: String?)

    fun parse(data: String, today: LocalDate = LocalDate.now()): Result? {
        if (data.length < 58 || data[0] != 'M') return null
        val legs = data[1].digitToIntOrNull() ?: return null
        val passenger = data.substring(2, 22).trim().replace('/', ' ').ifBlank { null }
        val flights = mutableListOf<ParsedFlight>()
        var pnr: String? = null
        var pos = 23
        repeat(legs) {
            if (data.length < pos + 35) return@repeat
            val legPnr = data.substring(pos, pos + 7).trim()
            if (pnr == null && legPnr.isNotEmpty()) pnr = legPnr
            val from = data.substring(pos + 7, pos + 10)
            val to = data.substring(pos + 10, pos + 13)
            val carrier = data.substring(pos + 13, pos + 16).trim()
            val number = data.substring(pos + 16, pos + 21).trim().trimStart('0').ifEmpty { "0" }
            val julian = data.substring(pos + 21, pos + 24).trim().toIntOrNull()
            val seat = data.substring(pos + 25, pos + 29).trim().trimStart('0').ifEmpty { null }
            val varSize = data.substring(pos + 35, minOf(data.length, pos + 37)).toIntOrNull(16) ?: 0
            if (from.all { it.isLetter() } && to.all { it.isLetter() } && carrier.length >= 2) {
                flights += ParsedFlight(
                    airlineCode = carrier,
                    number = number,
                    from = from,
                    to = to,
                    depDate = julian?.let { julianToDate(it, today) },
                    seat = seat,
                )
            }
            pos += 37 + varSize
        }
        if (flights.isEmpty()) return null
        return Result(flights, passenger, pnr)
    }

    /** День года без указания года — выбираем ближайший к сегодняшней дате. */
    fun julianToDate(dayOfYear: Int, today: LocalDate): LocalDate? {
        if (dayOfYear !in 1..366) return null
        return listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { y -> runCatching { LocalDate.ofYearDay(y, dayOfYear) }.getOrNull() }
            .minByOrNull { abs(it.toEpochDay() - today.toEpochDay()) }
    }

    /** Дополняет рейсы из текста данными из штрихкода (и наоборот). */
    fun merge(textFlights: List<ParsedFlight>, barcodeFlights: List<ParsedFlight>): List<ParsedFlight> {
        val result = textFlights.toMutableList()
        for (b in barcodeFlights) {
            val idx = result.indexOfFirst { it.key == b.key }
            if (idx < 0) {
                result += b
            } else {
                val t = result[idx]
                result[idx] = t.copy(
                    from = t.from ?: b.from,
                    to = t.to ?: b.to,
                    depDate = t.depDate ?: b.depDate,
                    seat = t.seat ?: b.seat,
                )
            }
        }
        return result.sortedWith(compareBy(nullsLast()) { it.depDate })
    }
}
