package com.travelplanner.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.travelplanner.app.ui.Format
import com.travelplanner.app.ui.Timeline
import com.travelplanner.app.ui.TimelineEntry
import com.travelplanner.app.ui.title
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Импорт настоящих файлов тем же кодом, что на телефоне (Android-версия PDFBox, сборка поездки, план).
 * ./gradlew testDebugUnitTest --tests '*DeviceImportTest*' -Ptickets.dir=/папка
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TravelApp::class)
class DeviceImportTest {
    @Test
    fun importFolder() = runBlocking {
        val dir = System.getProperty("tickets.dir")
        org.junit.Assume.assumeTrue(!dir.isNullOrBlank())
        val c = ApplicationProvider.getApplicationContext<TravelApp>().container
        val uris = File(dir).listFiles { f -> f.extension.equals("pdf", true) }!!.sortedBy { it.name }.map { Uri.fromFile(it) }
        uris.forEach { u ->
            val r = c.importer.import(u)
            println("DOC ${r.displayName}: ${r.document}")
        }
        val report = c.assembler.importAll(uris, null) { _, _, _ -> }
        println("REPORT trips=${report.trips} added=${report.added} dup=${report.duplicates} attention=${report.attention.map { it.title + it.missing }}")
        for ((tripId, tripTitle) in report.trips) {
            val flights = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.db.flights().forTrip(tripId) }
            val stays = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.db.stays().forTrip(tripId) }
            println("TRIP $tripTitle")
            Timeline.build(flights, stays, c.ref).forEach { e ->
                val t = Format.dateTime(e.at, e.zone)
                val d = when (e) {
                    is TimelineEntry.FlightEntry -> with(e.flight) {
                        "FLIGHT ${title()} ${if (isTrain) fromName else fromIata} -> ${if (isTrain) toName else toIata} arr " +
                            (arrivalMillis?.let { Format.dateTime(it, arrivalZone) + " ($arrivalZone)" } ?: "?")
                    }
                    is TimelineEntry.GoToAirport -> "GO ${e.flight.title()}"
                    is TimelineEntry.Layover -> "LAYOVER ${e.flight.fromIata}"
                    is TimelineEntry.Transfer -> "TRANSFER -> ${e.stay.name}"
                    is TimelineEntry.CheckIn -> "CHECKIN ${e.stay.name}"
                    is TimelineEntry.CheckOut -> "CHECKOUT ${e.stay.name}"
                }
                println("  $t ($e.zone) $d".replace("(${'$'}e.zone)", "[${e.zone}]"))
            }
        }
    }
}
