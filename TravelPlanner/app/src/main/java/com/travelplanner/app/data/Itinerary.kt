package com.travelplanner.app.data

import com.travelplanner.app.reference.ReferenceData

/**
 * Логика маршрута: пересадки и дорога в отель.
 * Используется и в плане поездки, и при постановке напоминаний.
 */
class Itinerary(private val ref: ReferenceData) {

    fun arrivalCity(f: Flight): String =
        if (f.isTrain) ref.citiesIn(f.toName).firstOrNull()?.city ?: f.toName else ref.cityName(f.toIata)

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

    companion object {
        private const val DAY = 24 * 3_600_000L
    }
}
