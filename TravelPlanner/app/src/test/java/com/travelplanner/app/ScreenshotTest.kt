package com.travelplanner.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.travelplanner.app.importer.Converters
import com.travelplanner.app.importer.TicketParser
import com.travelplanner.app.ui.TripScreen
import com.travelplanner.app.ui.TripsScreen
import com.travelplanner.app.ui.theme.TravelTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Скриншоты экранов на демо-поездке (вымышленные копии документов): папка build/outputs/roborazzi
 * Запуск: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = TravelApp::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var c: AppContainer
    private var tripId = 0L

    @Before
    fun seed() = runBlocking {
        org.junit.Assume.assumeTrue(System.getProperty("screenshots") == "true")
        c = ApplicationProvider.getApplicationContext<TravelApp>().container
        val ref = c.ref
        val names = mutableMapOf<String, String>()
        ref.airports.values.forEach { if (it.name.length >= 5 && it.name != it.city) names[it.name.uppercase()] = it.iata }
        ref.airports.values.groupBy { it.city }.forEach { (city, l) -> if (l.size == 1 && city.length >= 4) names[city.uppercase()] = l.first().iata }
        val parser = TicketParser(ref.airports.keys, ref.airlines.keys, names, LocalDate.now().minusDays(1)) { t -> ref.citiesIn(t).map { it.city } }
        tripId = c.repository.createTrip("Куала-Лумпур, ноябрь 2026")
        val docs = listOf(Samples.POBEDA, Samples.RZD, Samples.AGENCY).map { parser.parse(it) }
        docs.forEach { d ->
            d.flights.forEach { p -> Converters.flight(p, ref, tripId, 180, null, d.bookingRef, d.passenger)?.let { c.repository.saveFlight(it) } }
            d.trains.forEach { p -> Converters.train(p, ref, tripId, 45, null, d.bookingRef, d.passenger)?.let { c.repository.saveFlight(it) } }
        }
        listOf(Samples.HOTEL, Samples.TUTU_HOTEL).map { parser.parse(it) }.forEach { d ->
            d.stay?.let { p ->
                val zone = ref.zoneForPlace(p.address.orEmpty()) ?: ZoneId.of("Europe/Moscow")
                Converters.stay(p, zone, tripId, null, d.bookingRef)?.let { c.repository.saveStay(it) }
            }
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun home() {
        compose.setContent { TravelTheme { TripsScreen(c, {}, {}) } }
        shot("1_home")
    }

    @Test
    fun trip() {
        compose.setContent { TravelTheme { TripScreen(c, tripId, {}, {}, {}, {}) } }
        shot("2_plan")
        listOf("Погода и вещи" to "3_packing", "Приложения" to "4_apps").forEach { (tab, file) ->
            compose.onNodeWithText(tab).performClick()
            shot(file)
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkHome() {
        compose.setContent { TravelTheme { TripsScreen(c, {}, {}) } }
        shot("5_dark_home")
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkPlan() {
        compose.setContent { TravelTheme { TripScreen(c, tripId, {}, {}, {}, {}) } }
        shot("6_dark_plan")
        compose.onNodeWithText("Приложения").performClick()
        shot("7_dark_apps")
        compose.onNodeWithText("Погода и вещи").performClick()
        shot("8_dark_packing")
    }
}
