package com.travelplanner.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData

/**
 * Записывает перелёты и проживание в системный календарь (Google Календарь и т.п.)
 * с напоминанием. При изменении брони событие обновляется, при удалении — удаляется.
 */
class CalendarSync(private val context: Context, private val ref: ReferenceData) {

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun upsertFlight(f: Flight): Long? {
        val from = ref.airport(f.fromIata)
        val to = ref.airport(f.toIata)
        val title = "✈ ${f.airlineCode} ${f.flightNumber} ${from?.city ?: f.fromIata} → ${to?.city ?: f.toIata}"
        val location = buildString {
            append(from?.let { "Аэропорт ${it.name} (${it.iata}), ${it.city}" } ?: f.fromIata)
            if (f.departureTerminal.isNotBlank()) append(", терминал ${f.departureTerminal}")
        }
        val description = buildString {
            ref.airline(f.airlineCode)?.let { appendLine("Авиакомпания: ${it.name}") }
            if (f.bookingRef.isNotBlank()) appendLine("Бронирование: ${f.bookingRef}")
            if (f.seat.isNotBlank()) appendLine("Место: ${f.seat}")
            if (f.passenger.isNotBlank()) appendLine("Пассажир: ${f.passenger}")
            append("Добавлено приложением «Мои поездки»")
        }
        val end = f.arrivalMillis?.takeIf { it > f.departureMillis } ?: (f.departureMillis + 2 * 3_600_000L)
        return upsert(
            existingId = f.calendarEventId,
            title = title,
            location = location,
            description = description,
            start = f.departureMillis,
            end = end,
            startZone = f.departureZone,
            endZone = f.arrivalZone,
            reminderMinutes = listOf(f.remindMinutes, 24 * 60),
        )
    }

    fun upsertStay(s: Stay): Long? = upsert(
        existingId = s.calendarEventId,
        title = "🏨 ${s.name}",
        location = s.address,
        description = buildString {
            if (s.bookingRef.isNotBlank()) appendLine("Бронирование: ${s.bookingRef}")
            if (s.phone.isNotBlank()) appendLine("Телефон: ${s.phone}")
            if (s.notes.isNotBlank()) appendLine(s.notes)
            append("Добавлено приложением «Мои поездки»")
        },
        start = s.checkInMillis,
        end = s.checkOutMillis.coerceAtLeast(s.checkInMillis + 3_600_000L),
        startZone = s.zone,
        endZone = s.zone,
        reminderMinutes = listOf(120),
    )

    fun delete(eventId: Long?) {
        if (eventId == null || !hasPermission()) return
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null,
            )
        }
    }

    private fun upsert(
        existingId: Long?,
        title: String,
        location: String,
        description: String,
        start: Long,
        end: Long,
        startZone: String,
        endZone: String,
        reminderMinutes: List<Int>,
    ): Long? {
        if (!hasPermission()) return existingId
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, startZone)
            put(CalendarContract.Events.EVENT_END_TIMEZONE, endZone)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val resolver = context.contentResolver
        return runCatching {
            if (existingId != null) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                if (resolver.update(uri, values, null, null) > 0) {
                    resolver.delete(
                        CalendarContract.Reminders.CONTENT_URI,
                        "${CalendarContract.Reminders.EVENT_ID}=?", arrayOf(existingId.toString()),
                    )
                    addReminders(existingId, reminderMinutes)
                    return@runCatching existingId
                }
            }
            val calendarId = findCalendarId() ?: return@runCatching null
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return@runCatching null
            val id = ContentUris.parseId(uri)
            addReminders(id, reminderMinutes)
            id
        }.getOrNull()
    }

    private fun addReminders(eventId: Long, minutes: List<Int>) {
        minutes.distinct().forEach { m ->
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, m)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
        }
    }

    /** Основной календарь пользователя, в который можно писать. */
    private fun findCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE}=1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
            var best: Long? = null
            var bestScore = -1
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val primary = c.getInt(1) == 1
                val google = c.getString(2) == "com.google"
                val score = (if (primary) 2 else 0) + (if (google) 1 else 0)
                if (score > bestScore) {
                    best = id
                    bestScore = score
                }
            }
            return best
        }
        return null
    }
}
