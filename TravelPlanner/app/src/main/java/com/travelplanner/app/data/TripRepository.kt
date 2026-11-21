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
    private val itinerary: Itinerary,
) {
    fun trips() = db.trips().observeAll()
    fun trip(id: Long) = db.trips().observe(id)
    fun allFlights() = db.flights().observeAll()
    fun allStays() = db.stays().observeAll()
    fun flights(tripId: Long) = db.flights().observeForTrip(tripId)
    fun stays(tripId: Long) = db.stays().observeForTrip(tripId)
    fun documents(tripId: Long) = db.documents().observeForTrip(tripId)

    suspend fun addDocument(doc: TripDocument): Long = db.documents().insert(doc)

    suspend fun createTrip(title: String): Long = db.trips().insert(Trip(title = title.ifBlank { "Новая поездка" }))

    suspend fun renameTrip(trip: Trip, title: String) = db.trips().update(trip.copy(title = title))

    suspend fun deleteTrip(trip: Trip) = withContext(Dispatchers.IO) {
        db.flights().forTrip(trip.id).forEach { removeFlightSideEffects(it) }
        db.stays().forTrip(trip.id).forEach { removeStaySideEffects(it) }
        val files = db.documents().forTrip(trip.id).map { it.path } +
            db.flights().forTrip(trip.id).mapNotNull { it.attachmentPath } +
            db.stays().forTrip(trip.id).mapNotNull { it.attachmentPath }
        db.trips().delete(trip)
        files.distinct().forEach { File(it).delete() }
    }

    suspend fun saveFlight(flight: Flight): Long = withContext(Dispatchers.IO) {
        val id = if (flight.id == 0L) db.flights().insert(flight) else flight.id.also { db.flights().update(flight) }
        // Новый рейс может сделать соседний рейс пересадкой (или наоборот) — пересчитываем всю поездку
        refreshTrip(flight.tripId)
        id
    }

    /** Пересчитывает напоминания и события календаря перелётов поездки с учётом пересадок. */
    suspend fun refreshTrip(tripId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val flights = db.flights().forTrip(tripId)
        val stays = db.stays().forTrip(tripId)
        flights.forEach { f ->
            val connection = itinerary.connectionFrom(f, flights, stays) != null
            if (settings.calendarSync) {
                val eventId = calendar.upsertFlight(f, connection)
                if (eventId != f.calendarEventId) db.flights().update(f.copy(calendarEventId = eventId))
            }
            scheduler.scheduleFlight(f, now, connection)
        }
        stays.forEach { s -> scheduler.scheduleStay(s, now, itinerary.leaveStayAt(s, flights, stays)) }
    }

    suspend fun deleteFlight(flight: Flight) = withContext(Dispatchers.IO) {
        removeFlightSideEffects(flight)
        db.flights().delete(flight)
        refreshTrip(flight.tripId)
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
        // Отель между рейсами отменяет «пересадку»
        refreshTrip(stay.tripId)
        id
    }

    suspend fun deleteStay(stay: Stay) = withContext(Dispatchers.IO) {
        removeStaySideEffects(stay)
        db.stays().delete(stay)
    }

    /** Повторно ставит будильники (после перезагрузки) и, если нужно, досинхронизирует календарь. */
    suspend fun rescheduleAll(syncCalendar: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val tripIds = (db.flights().upcoming(now).map { it.tripId } + db.stays().upcoming(now).map { it.tripId }).distinct()
        tripIds.forEach { tripId ->
            if (syncCalendar && settings.calendarSync) {
                refreshTrip(tripId)
                db.stays().forTrip(tripId).forEach { saveStayCalendarOnly(it) }
            } else {
                val flights = db.flights().forTrip(tripId)
                val stays = db.stays().forTrip(tripId)
                flights.forEach { f -> scheduler.scheduleFlight(f, now, itinerary.connectionFrom(f, flights, stays) != null) }
                stays.forEach { s -> scheduler.scheduleStay(s, now, itinerary.leaveStayAt(s, flights, stays)) }
            }
        }
    }

    private suspend fun saveStayCalendarOnly(s: Stay) {
        val eventId = calendar.upsertStay(s)
        if (eventId != s.calendarEventId) db.stays().update(s.copy(calendarEventId = eventId))
    }

    private fun removeFlightSideEffects(f: Flight) {
        scheduler.cancelFlight(f.id)
        calendar.delete(f.calendarEventId)
    }

    private fun removeStaySideEffects(s: Stay) {
        scheduler.cancelStay(s.id)
        calendar.delete(s.calendarEventId)
    }
}
