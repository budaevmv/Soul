package com.travelplanner.app.importer

import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Пункт поездки при сборке: переезд (перелёт/поезд) или проживание. */
sealed class PlanItem(val start: Long, val end: Long, val file: String) {
    class Move(val flight: Flight, file: String) : PlanItem(flight.departureMillis, flight.arrivalMillis ?: flight.departureMillis, file)
    class Hotel(val stay: Stay, file: String) : PlanItem(stay.checkInMillis, stay.checkOutMillis, file)
}

/** Раскладывает пункты по поездкам и придумывает поездке название. */
class TripGrouping(private val ref: ReferenceData) {

    /** Пункты, между которыми больше 3 суток, считаем разными поездками. */
    fun cluster(items: List<PlanItem>): List<List<PlanItem>> {
        val groups = mutableListOf<MutableList<PlanItem>>()
        var end = Long.MIN_VALUE
        for (item in items.sortedBy { it.start }) {
            if (groups.isEmpty() || item.start > end + GAP) groups += mutableListOf<PlanItem>()
            groups.last() += item
            end = maxOf(end, item.end)
        }
        return groups
    }

    /**
     * «Куала-Лумпур, ноябрь 2026». Если маршрут не возвращается домой — это последний город;
     * если возвращается — город, где проводим больше всего времени (пересадки короче).
     */
    fun title(group: List<PlanItem>, zone: ZoneId = ZoneId.systemDefault()): String {
        val moves = group.filterIsInstance<PlanItem.Move>().map { it.flight }.sortedBy { it.departureMillis }
        val stays = group.filterIsInstance<PlanItem.Hotel>().map { it.stay }
        val home = moves.firstOrNull()?.let { city(it, from = true) }
        val last = moves.lastOrNull()?.let { city(it, from = false) }
        val dest = if (last != null && last != home) {
            last
        } else {
            val durations = mutableMapOf<String, Long>()
            moves.forEachIndexed { i, f ->
                val c = city(f, from = false)
                if (c == home) return@forEachIndexed
                val arrive = f.arrivalMillis ?: f.departureMillis
                val leave = moves.getOrNull(i + 1)?.departureMillis ?: arrive
                durations[c] = (durations[c] ?: 0) + (leave - arrive)
            }
            durations.maxByOrNull { it.value }?.key
                ?: stays.firstOrNull()?.let { s -> ref.citiesIn(s.address).firstOrNull()?.city ?: s.name }
                ?: "Поездка"
        }
        val start = group.minOf { it.start }
        val month = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))
            .format(Instant.ofEpochMilli(start).atZone(zone))
        return "$dest, $month"
    }

    private fun city(f: Flight, from: Boolean): String {
        val name = if (from) f.fromName else f.toName
        val iata = if (from) f.fromIata else f.toIata
        return if (f.isTrain) ref.citiesIn(name).firstOrNull()?.city ?: name else ref.cityName(iata)
    }

    companion object {
        const val GAP = 3L * 24 * 3_600_000L
    }
}
