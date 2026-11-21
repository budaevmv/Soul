package com.travelplanner.app.data

import com.travelplanner.app.calendar.CalendarSync
import com.travelplanner.app.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TripRepository(
    val db: AppDatabase,
    private val scheduler: ReminderScheduler,
    private val calendar: CalendarSync,
    private val settings: Settings,
) {
    fun trips() = db.trips().observeAll()
    fun trip(id: Long) = db.trips().observe(id)
    fun allFlights() = db.flights().observeAll()
    fun allStays() = db.stays().observeAll()
    fun flights(tripId: Long) = db.flights().observeForTrip(tripId)
    fun stays(tripId: Long) = db.stays().observeForTrip(tripId)

    suspend fun createTrip(title: String): Long = db.trips().insert(Trip(title = title.ifBlank { "Новая поездка" }))

    suspend fun renameTrip(trip: Trip, title: String) = db.trips().update(trip.copy(title = title))

    suspend fun deleteTrip(trip: Trip) = withContext(Dispatchers.IO) {
        db.flights().forTrip(trip.id).forEach { removeFlightSideEffects(it) }
        db.stays().forTrip(trip.id).forEach { removeStaySideEffects(it) }
        db.trips().delete(trip)
    }

    suspend fun saveFlight(flight: Flight): Long = withContext(Dispatchers.IO) {
        val id = if (flight.id == 0L) db.flights().insert(flight) else flight.id.also { db.flights().update(flight) }
        var saved = flight.copy(id = id)
        if (settings.calendarSync) {
            val eventId = calendar.upsertFlight(saved)
            if (eventId != saved.calendarEventId) {
                saved = saved.copy(calendarEventId = eventId)
                db.flights().update(saved)
            }
        }
        scheduler.scheduleFlight(saved)
        id
    }

    suspend fun deleteFlight(flight: Flight) = withContext(Dispatchers.IO) {
        removeFlightSideEffects(flight)
        db.flights().delete(flight)
    }

    suspend fun saveStay(stay: Stay): Long = withContext(Dispatchers.IO) {
        val id = if (stay.id == 0L) db.stays().insert(stay) else stay.id.also { db.stays().update(stay) }
        var saved = stay.copy(id = id)
        if (settings.calendarSync) {
            val eventId = calendar.upsertStay(saved)
            if (eventId != saved.calendarEventId) {
                saved = saved.copy(calendarEventId = eventId)
                db.stays().update(saved)
            }
        }
        scheduler.scheduleStay(saved)
        id
    }

    suspend fun deleteStay(stay: Stay) = withContext(Dispatchers.IO) {
        removeStaySideEffects(stay)
        db.stays().delete(stay)
    }

    /** Повторно ставит будильники (после перезагрузки) и, если нужно, досинхронизирует календарь. */
    suspend fun rescheduleAll(syncCalendar: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.flights().upcoming(now).forEach { f ->
            if (syncCalendar && settings.calendarSync) saveFlight(f) else scheduler.scheduleFlight(f, now)
        }
        db.stays().upcoming(now).forEach { s ->
            if (syncCalendar && settings.calendarSync) saveStay(s) else scheduler.scheduleStay(s, now)
        }
    }

    private fun removeFlightSideEffects(f: Flight) {
        scheduler.cancelFlight(f.id)
        calendar.delete(f.calendarEventId)
        f.attachmentPath?.let { File(it).delete() }
    }

    private fun removeStaySideEffects(s: Stay) {
        scheduler.cancelStay(s.id)
        calendar.delete(s.calendarEventId)
        s.attachmentPath?.let { File(it).delete() }
    }
}
