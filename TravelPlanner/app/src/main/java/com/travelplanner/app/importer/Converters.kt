package com.travelplanner.app.importer

import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Перевод распознанных данных в записи базы. Возвращают null, если данных не хватает. */
object Converters {
    private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

    private fun millis(d: LocalDate, t: LocalTime, z: ZoneId) = LocalDateTime.of(d, t).atZone(z).toInstant().toEpochMilli()

    fun missing(p: ParsedFlight): List<String> = buildList {
        if (p.from == null) add("аэропорт вылета")
        if (p.to == null) add("аэропорт прилёта")
        if (p.depDate == null) add("дата вылета")
        if (p.depTime == null) add("время вылета")
    }

    fun missing(p: ParsedTrain): List<String> = buildList {
        if (p.from == null) add("станция отправления")
        if (p.to == null) add("станция прибытия")
        if (p.depDate == null) add("дата отправления")
        if (p.depTime == null) add("время отправления")
    }

    fun missing(p: ParsedStay): List<String> = buildList {
        if (p.checkOut == null) add("дата выезда")
    }

    fun flight(p: ParsedFlight, ref: ReferenceData, tripId: Long, remindMinutes: Int, attachment: String?, booking: String?, passenger: String?): Flight? {
        if (missing(p).isNotEmpty()) return null
        val depZone = ref.zoneFor(p.from)
        val arrZone = ref.zoneFor(p.to)
        val dep = millis(p.depDate!!, p.depTime!!, depZone)
        val arr = p.arrTime?.let { t ->
            val a = millis(p.arrDate ?: p.depDate, t, arrZone)
            if (a <= dep) a + 24 * 3_600_000L else a
        }
        return Flight(
            tripId = tripId, airlineCode = p.airlineCode, flightNumber = p.number,
            fromIata = p.from!!, toIata = p.to!!,
            departureMillis = dep, departureZone = depZone.id, arrivalMillis = arr, arrivalZone = arrZone.id,
            departureTerminal = p.terminal.orEmpty(), seat = p.seat.orEmpty(), bookingRef = booking.orEmpty(),
            passenger = passenger.orEmpty(), remindMinutes = remindMinutes, attachmentPath = attachment,
        )
    }

    fun train(p: ParsedTrain, ref: ReferenceData, tripId: Long, remindMinutes: Int, attachment: String?, booking: String?, passenger: String?): Flight? {
        if (missing(p).isNotEmpty()) return null
        val depZone = ref.zoneForPlace(p.from!!) ?: ZoneId.systemDefault()
        val arrZone = ref.zoneForPlace(p.to!!) ?: depZone
        // В билетах РЖД время бывает московским — тогда считаем по Москве, а показываем по местному
        val depCalc = if (p.moscowTime) MOSCOW else depZone
        val arrCalc = if (p.moscowTime) MOSCOW else arrZone
        val dep = millis(p.depDate!!, p.depTime!!, depCalc)
        val arr = p.arrTime?.let { t ->
            val a = millis(p.arrDate ?: p.depDate, t, arrCalc)
            if (a <= dep) a + 24 * 3_600_000L else a
        }
        return Flight(
            tripId = tripId, airlineCode = "", flightNumber = p.number, fromIata = "", toIata = "",
            departureMillis = dep, departureZone = depZone.id, arrivalMillis = arr, arrivalZone = arrZone.id,
            seat = p.seat.orEmpty(), bookingRef = booking.orEmpty(), passenger = passenger.orEmpty(),
            remindMinutes = remindMinutes, attachmentPath = attachment,
            mode = Flight.MODE_TRAIN, fromName = titleCase(p.from), toName = titleCase(p.to), car = p.car.orEmpty(),
        )
    }

    fun stay(p: ParsedStay, zone: ZoneId, tripId: Long, attachment: String?, booking: String?): Stay? {
        val out = p.checkOut ?: return null
        val inMs = millis(p.checkIn, p.checkInTime, zone)
        val outMs = millis(out, p.checkOutTime, zone).coerceAtLeast(inMs)
        return Stay(
            tripId = tripId, name = p.name ?: p.address?.substringBefore(',') ?: "Жильё", address = p.address.orEmpty(),
            checkInMillis = inMs, checkOutMillis = outMs, zone = zone.id, bookingRef = booking.orEmpty(),
            phone = p.phone.orEmpty(), attachmentPath = attachment,
        )
    }

    /** «МОСКВА БЕЛОРУССКАЯ» -> «Москва Белорусская». */
    fun titleCase(s: String): String = s.lowercase().split(' ').joinToString(" ") { w ->
        w.split('-').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun stayKey(s: Stay): String = s.name.lowercase().filter { it.isLetterOrDigit() } + "|" + s.checkInMillis / 86_400_000L
}
