package com.travelplanner.app

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.travelplanner.app.calendar.CalendarSync
import com.travelplanner.app.data.AppDatabase
import com.travelplanner.app.data.Itinerary
import com.travelplanner.app.data.TravelTime
import com.travelplanner.app.data.Settings
import com.travelplanner.app.data.TripRepository
import com.travelplanner.app.importer.TicketImporter
import com.travelplanner.app.importer.TripAssembler
import com.travelplanner.app.reference.ReferenceData
import com.travelplanner.app.reminders.ReminderReceiver
import com.travelplanner.app.reminders.ReminderScheduler

class TravelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderReceiver.createChannel(this)
        PDFBoxResourceLoader.init(this)
    }
}

class AppContainer(context: Context) {
    val ref: ReferenceData by lazy { ReferenceData.load(context) }
    val db: AppDatabase by lazy { AppDatabase.create(context) }
    val settings = Settings(context)
    val scheduler by lazy { ReminderScheduler(context, settings, ref) }
    val calendar by lazy { CalendarSync(context, ref) }
    val repository by lazy { TripRepository(db, scheduler, calendar, settings, Itinerary(ref), TravelTime(context)) }
    val importer by lazy { TicketImporter(context, ref) }
    val assembler by lazy { TripAssembler(importer, repository, ref, settings) }

    /** Черновики для редакторов: пункты, распознанные не полностью. */
    var draftFlight: com.travelplanner.app.ui.FlightForm? = null
    var draftStay: com.travelplanner.app.ui.StayForm? = null
}
