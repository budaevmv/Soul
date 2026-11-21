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
import com.travelplanner.app.ui.ImportScreen
import com.travelplanner.app.ui.StayEditScreen
import com.travelplanner.app.ui.TripScreen
import com.travelplanner.app.ui.TripsScreen
import com.travelplanner.app.ui.theme.TravelTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Файл, которым поделились из другого приложения. */
    private var sharedUri by mutableStateOf<Uri?>(null)
    private var openTripId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as TravelApp).container
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch { c.repository.rescheduleAll() }

        setContent {
            TravelTheme {
                val nav = rememberNavController()
                LaunchedEffect(sharedUri) {
                    if (sharedUri != null) nav.navigate("import/0?shared=true")
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
                        val uri = if (shared) sharedUri else null
                        ImportScreen(
                            c, e.arguments!!.getLong("trip"), uri,
                            onBack = {
                                sharedUri = null
                                nav.popBackStack()
                            },
                            onSaved = { tripId ->
                                sharedUri = null
                                nav.navigate("trip/$tripId") { popUpTo("trips") }
                            },
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
        sharedUri = when (intent.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let { saveSharedText(it) }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    /** Текст, которым поделились (например, письмо с бронью), сохраняем во временный файл для импорта. */
    private fun saveSharedText(text: String): Uri {
        val file = java.io.File(cacheDir, "shared_${System.currentTimeMillis()}.txt").apply { writeText(text) }
        return Uri.fromFile(file)
    }

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
    }
}
