package com.travelplanner.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.travelplanner.app.ui.FlightEditScreen
import com.travelplanner.app.ui.BulkImportScreen
import com.travelplanner.app.ui.StayEditScreen
import com.travelplanner.app.ui.TripScreen
import com.travelplanner.app.ui.TripsScreen
import com.travelplanner.app.ui.theme.TravelTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Файлы, которыми поделились из другого приложения. */
    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())
    private var openTripId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as TravelApp).container
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            // После обновления (пересадки, выезд из отеля) один раз обновляем и события календаря
            val refreshCalendar = c.settings.calendarLogicVersion < CALENDAR_LOGIC_VERSION && c.calendar.hasPermission()
            c.repository.rescheduleAll(syncCalendar = refreshCalendar)
            if (refreshCalendar) c.settings.calendarLogicVersion = CALENDAR_LOGIC_VERSION
        }

        setContent {
            TravelTheme {
                val nav = rememberNavController()
                LaunchedEffect(sharedUris) {
                    if (sharedUris.isNotEmpty()) nav.navigate("import/0?shared=true")
                }
                LaunchedEffect(openTripId) {
                    openTripId?.let {
                        nav.navigate("trip/$it")
                        openTripId = null
                    }
                }
                NavHost(nav, startDestination = "trips") {
                    composable("trips") {
                        TripsScreen(c, onOpenTrip = { nav.navigate("trip/$it") }, onImport = { nav.navigate("import/0") })
                    }
                    composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                        val id = e.arguments!!.getLong("id")
                        TripScreen(
                            c, id,
                            onBack = { nav.popBackStack() },
                            onEditFlight = { nav.navigate("flight/$id/$it") },
                            onEditStay = { nav.navigate("stay/$id/$it") },
                            onImport = { nav.navigate("import/$id") },
                        )
                    }
                    composable(
                        "flight/{trip}/{id}",
                        arguments = listOf(navArgument("trip") { type = NavType.LongType }, navArgument("id") { type = NavType.LongType }),
                    ) { e ->
                        FlightEditScreen(c, e.arguments!!.getLong("trip"), e.arguments!!.getLong("id")) { nav.popBackStack() }
                    }
                    composable(
                        "stay/{trip}/{id}",
                        arguments = listOf(navArgument("trip") { type = NavType.LongType }, navArgument("id") { type = NavType.LongType }),
                    ) { e ->
                        StayEditScreen(c, e.arguments!!.getLong("trip"), e.arguments!!.getLong("id")) { nav.popBackStack() }
                    }
                    composable(
                        "import/{trip}?shared={shared}",
                        arguments = listOf(
                            navArgument("trip") { type = NavType.LongType },
                            navArgument("shared") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) { e ->
                        val shared = e.arguments!!.getBoolean("shared")
                        val uris = if (shared) sharedUris else emptyList()
                        BulkImportScreen(
                            c, e.arguments!!.getLong("trip"), uris,
                            onBack = {
                                sharedUris = emptyList()
                                nav.popBackStack()
                            },
                            onOpenTrip = { tripId ->
                                sharedUris = emptyList()
                                nav.navigate("trip/$tripId") { popUpTo("trips") }
                            },
                            onFixFlight = { tripId -> nav.navigate("flight/$tripId/0") },
                            onFixStay = { tripId -> nav.navigate("stay/$tripId/0") },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1)
        if (tripId > 0) {
            openTripId = tripId
            return
        }
        sharedUris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let { saveSharedText(it) },
            )
            Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
    }

    /** Текст, которым поделились (например, письмо с бронью), сохраняем во временный файл для импорта. */
    private fun saveSharedText(text: String): Uri {
        val file = java.io.File(cacheDir, "shared_${System.currentTimeMillis()}.txt").apply { writeText(text) }
        return Uri.fromFile(file)
    }

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        private const val CALENDAR_LOGIC_VERSION = 2
    }
}
