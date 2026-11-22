package com.travelplanner.app.data

import com.travelplanner.app.reference.ReferenceData

/**
 * Логика маршрута: пересадки и дорога в отель.
 * Используется и в плане поездки, и при постановке напоминаний.
 */
class Itinerary(private val ref: ReferenceData) {

    fun arrivalCity(f: Flight): String =
        if (f.isTrain) ref.citiesIn(f.toName).firstOrNull()?.city ?: f.toName else ref.cityName(f.toIata)

    /** Отель, из которого выезжаем на этот рейс/поезд: живём в нём перед отправлением и в том же городе. */
    fun stayBefore(f: Flight, stays: List<Stay>): Stay? {
        val city = departureCity(f)
        return stays.filter { s -> s.tripId == f.tripId && s.checkInMillis < f.departureMillis && s.checkOutMillis + 6 * 3_600_000L > f.departureMillis }
            .filter { s -> stayCity(s).let { it == null || it == city } }
            .maxByOrNull { it.checkInMillis }
    }

    fun airportName(iata: String): String = ref.airport(iata)?.let { "${it.name} ${it.city} $iata" } ?: "$iata airport"

    fun departureCity(f: Flight): String =
        if (f.isTrain) ref.citiesIn(f.fromName).firstOrNull()?.city ?: f.fromName else ref.cityName(f.fromIata)

    private fun stayCity(s: Stay): String? = ref.citiesIn(s.address + " " + s.name).firstOrNull()?.city

    /**
     * Если перелёт — пересадка (прилетели в тот же аэропорт меньше чем за сутки до вылета
     * и между рейсами нет отеля), возвращает предыдущий рейс. Тогда «выезд в аэропорт» не нужен.
     */
    fun connectionFrom(f: Flight, moves: List<Flight>, stays: List<Stay>): Flight? {
        if (f.isTrain) return null
        val prev = moves.filter { it.tripId == f.tripId && it.id != f.id && !it.isTrain && it.toIata == f.fromIata }
            .filter { p -> p.arrivalMillis != null && p.arrivalMillis <= f.departureMillis && f.departureMillis - p.arrivalMillis < DAY }
            .maxByOrNull { it.arrivalMillis!! } ?: return null
        val stayBetween = stays.any { s ->
            s.tripId == f.tripId && s.checkOutMillis > prev.arrivalMillis!! && s.checkInMillis < f.departureMillis &&
                (stayCity(s) == null || stayCity(s) == arrivalCity(prev))
        }
        return if (stayBetween) null else prev
    }

    /** Рейс/поезд, после которого едем в этот отель: прилёт в город отеля, не пересадка. */
    fun arrivalToStay(s: Stay, moves: List<Flight>, stays: List<Stay>): Flight? {
        val city = stayCity(s)
        return moves.filter { it.tripId == s.tripId && it.arrivalMillis != null }
            .filter { a -> a.arrivalMillis!! < s.checkOutMillis && a.arrivalMillis >= s.checkInMillis - DAY }
            .filter { a -> city == null || arrivalCity(a) == city }
            .filter { a -> moves.none { n -> connectionFrom(n, moves, stays)?.id == a.id } }
            .maxByOrNull { it.arrivalMillis!! }
    }

    /**
     * Когда на самом деле уезжаем из отеля: если до расчётного часа нужно выезжать на вокзал/в аэропорт —
     * за минуту до этого. Иначе null (выезд в расчётный час).
     */
    fun leaveStayAt(s: Stay, moves: List<Flight>, stays: List<Stay>): Long? {
        val arrivedAt = arrivalToStay(s, moves, stays)?.arrivalMillis?.coerceAtLeast(s.checkInMillis) ?: s.checkInMillis
        return moves.filter { it.tripId == s.tripId && it.departureMillis > arrivedAt && it.departureMillis - it.remindMinutes * 60_000L < s.checkOutMillis }
            .minByOrNull { it.departureMillis }
            ?.let { (it.departureMillis - it.remindMinutes * 60_000L - 60_000L).coerceAtLeast(arrivedAt + 1) }
    }

    /** Остановка: город, где проводим время (не пересадка и не дом). */
    data class Stop(val city: String, val country: String?, val start: Long, val end: Long, val zone: String)

    fun arrivalCountry(f: Flight): String? =
        if (f.isTrain) ref.citiesIn(f.toName).firstOrNull()?.country else ref.airport(f.toIata)?.country

    fun departureCountry(f: Flight): String? =
        if (f.isTrain) ref.citiesIn(f.fromName).firstOrNull()?.country else ref.airport(f.fromIata)?.country

    fun stops(flights: List<Flight>, stays: List<Stay>): List<Stop> {
        val moves = flights.sortedBy { it.departureMillis }
        if (moves.isEmpty()) {
            return stays.map { s ->
                val a = ref.citiesIn(s.address + " " + s.name).firstOrNull()
                Stop(a?.city ?: s.name, a?.country, s.checkInMillis, s.checkOutMillis, s.zone)
            }
        }
        val home = departureCity(moves.first())
        val result = mutableListOf<Stop>()
        moves.forEachIndexed { i, f ->
            val next = moves.getOrNull(i + 1)
            if (next != null && connectionFrom(next, moves, stays)?.id == f.id) return@forEachIndexed
            val city = arrivalCity(f)
            if (city == home && next == null) return@forEachIndexed
            val start = f.arrivalMillis ?: f.departureMillis
            val end = next?.departureMillis ?: (start + DAY)
            val last = result.lastOrNull()
            if (last != null && last.city == city) result[result.lastIndex] = last.copy(end = end)
            else result += Stop(city, arrivalCountry(f), start, end, f.arrivalZone)
        }
        return result
    }

    companion object {
        private const val DAY = 24 * 3_600_000L
    }
}
