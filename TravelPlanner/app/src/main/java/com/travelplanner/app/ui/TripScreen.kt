package com.travelplanner.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    c: AppContainer,
    tripId: Long,
    onBack: () -> Unit,
    onEditFlight: (Long) -> Unit,
    onEditStay: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val trip by c.repository.trip(tripId).collectAsState(initial = null)
    val flights by c.repository.flights(tripId).collectAsState(initial = emptyList())
    val stays by c.repository.stays(tripId).collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.title ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Добавить перелёт вручную") }, leadingIcon = { Icon(Icons.Default.Flight, null) }, onClick = {
                            menu = false
                            onEditFlight(0)
                        })
                        DropdownMenuItem(text = { Text("Добавить жильё вручную") }, leadingIcon = { Icon(Icons.Default.Hotel, null) }, onClick = {
                            menu = false
                            onEditStay(0)
                        })
                        DropdownMenuItem(text = { Text("Поделиться планом") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                            menu = false
                            sharePlan(context, c, trip?.title ?: "Поездка", flights, stays)
                        })
                        DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = {
                            menu = false
                            rename = true
                        })
                        DropdownMenuItem(text = { Text("Удалить поездку") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = {
                            menu = false
                            confirmDelete = true
                        })
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0 || tab == 5) {
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(Icons.Default.UploadFile, null) },
                    text = { Text("Загрузить документы") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf("🗓 План", "☀️ Погода и вещи", "📱 Приложения", "🌍 Страна", "✈️ Авиакомпании", "📄 Документы")
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> PlanTab(c, flights, stays, onEditFlight, onEditStay)
                1 -> WeatherPackingTab(c, tripId, flights, stays)
                2 -> AppsTab(c, flights, stays)
                3 -> CountriesTab(c.ref, flights)
                4 -> AirlinesTab(c.ref, flights)
                5 -> DocumentsTab(c, tripId)
            }
        }
    }

    if (rename) {
        var title by remember { mutableStateOf(trip?.title ?: "") }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Название поездки") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    rename = false
                    trip?.let { t -> scope.launch { c.repository.renameTrip(t, title) } }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Отмена") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить поездку?") },
            text = { Text("Будут удалены перелёты, жильё, напоминания и события в календаре.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    trip?.let { t ->
                        scope.launch {
                            c.repository.deleteTrip(t)
                            onBack()
                        }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PlanTab(
    c: AppContainer,
    flights: List<Flight>,
    stays: List<Stay>,
    onEditFlight: (Long) -> Unit,
    onEditStay: (Long) -> Unit,
) {
    val entries = remember(flights, stays) { Timeline.build(flights, stays, c.ref) }
    val now = System.currentTimeMillis()
    val next = Timeline.next(entries, now)
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
    ) {
        item { TripHeader(c, flights, stays, now) }
        if (entries.isEmpty()) {
            item {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("План пока пуст", style = MaterialTheme.typography.titleLarge)
                    Text("Нажмите «Загрузить документы» и выберите билеты и брони — план соберётся сам.")
                }
            }
        }
        var lastDay = ""
        entries.forEachIndexed { index, e ->
            val day = Format.day(e.at, e.zone)
            if (day != lastDay) {
                lastDay = day
                item(key = "day-$index") {
                    Text(
                        day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 50.dp, top = 12.dp, bottom = 6.dp),
                    )
                }
            }
            item(key = "entry-$index") {
                Column {
                    if (e === next) {
                        Text(
                            "Следующее · ${Format.countdown(e.at, now)}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 50.dp, bottom = 4.dp),
                        )
                    }
                    TimelineRow(
                        e, c, { onEditFlight(it.id) }, { onEditStay(it.id) },
                        first = index == 0, last = index == entries.lastIndex, highlight = e === next, now = now,
                    )
                }
            }
        }
    }
}

@Composable
private fun AirlinesTab(ref: ReferenceData, flights: List<Flight>) {
    val codes = flights.filter { !it.isTrain }.map { it.airlineCode }.distinct()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (codes.isEmpty()) item { Text("В поездке нет перелётов — здесь появятся правила ваших авиакомпаний.") }
        items(codes) { code -> AirlineCard(code, ref.airline(code)) }
        item { GeneralRulesCard() }
    }
}

/** Страны назначения: прилёты, кроме страны «дома» (первого вылета). Для внутренних поездок — домашняя страна. */
fun destinationCountries(ref: ReferenceData, flights: List<Flight>): List<String> {
    val sorted = flights.sortedBy { it.departureMillis }
    fun countryFrom(f: Flight) = if (f.isTrain) ref.citiesIn(f.fromName).firstOrNull()?.country else ref.airport(f.fromIata)?.country
    fun countryTo(f: Flight) = if (f.isTrain) ref.citiesIn(f.toName).firstOrNull()?.country else ref.airport(f.toIata)?.country
    val home = sorted.firstOrNull()?.let { countryFrom(it) }
    val arrivals = sorted.mapNotNull { countryTo(it) }.distinct()
    val abroad = arrivals.filter { it != home }
    return abroad.ifEmpty { listOfNotNull(home) }
}

@Composable
private fun CountriesTab(ref: ReferenceData, flights: List<Flight>) {
    val countries = destinationCountries(ref, flights)
    var browse by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (countries.isEmpty()) item { Text("Добавьте перелёты — здесь появится справка по стране назначения. Или выберите страну из списка ниже.") }
        items(countries) { code -> CountryCard(code, ref.country(code)) }
        browse?.takeIf { it !in countries }?.let { code -> item { CountryCard(code, ref.country(code)) } }
        item {
            Text("Справочник стран", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(ref.countries.entries.sortedBy { it.value.name }.toList()) { (code, info) ->
            Text(
                "${info.flag} ${info.name}",
                Modifier.fillMaxWidth().clickable { browse = code }.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DocumentsTab(c: AppContainer, tripId: Long) {
    val context = LocalContext.current
    val docs by c.repository.documents(tripId).collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Все загруженные файлы поездки — открываются без интернета", style = MaterialTheme.typography.bodyMedium)
        }
        if (docs.isEmpty()) item { Text("Загруженные билеты и брони появятся здесь.") }
        items(docs) { d ->
            val (icon, color) = when (d.kind) {
                "FLIGHT" -> Icons.Default.Flight to com.travelplanner.app.ui.theme.TripColors.Flight
                "TRAIN" -> Icons.Default.Train to com.travelplanner.app.ui.theme.TripColors.Train
                "HOTEL" -> Icons.Default.Hotel to com.travelplanner.app.ui.theme.TripColors.Hotel
                else -> Icons.Default.Description to com.travelplanner.app.ui.theme.TripColors.Checkout
            }
            Card(Modifier.fillMaxWidth().clickable { openDocument(context, File(d.path)) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(color, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(d.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

fun openDocument(context: Context, file: File) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val mime = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> context.contentResolver.getType(uri) ?: "*/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Открыть документ")) }
}
