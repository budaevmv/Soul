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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Train
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(trip?.title ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 1) },
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
                    icon = { Icon(Icons.Outlined.FileUpload, null) },
                    text = { Text("Документы", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(50),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf("План", "Погода и вещи", "Приложения", "Страна", "Авиакомпании", "Документы")
            ScrollableTabRow(
                selectedTabIndex = tab, edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {},
                indicator = { positions ->
                    if (tab < positions.size) {
                        Box(
                            Modifier.tabIndicatorOffset(positions[tab]).height(3.dp).padding(horizontal = 28.dp)
                                .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface),
                        )
                    }
                },
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(t, style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
            Crossfade(tab, label = "tabs") { current ->
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        0 -> PlanTab(c, flights, stays, onEditFlight, onEditStay)
                        1 -> WeatherPackingTab(c, tripId, flights, stays)
                        2 -> AppsTab(c, flights, stays)
                        3 -> CountriesTab(c.ref, flights)
                        4 -> AirlinesTab(c.ref, flights)
                        5 -> DocumentsTab(c, tripId)
                    }
                }
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
    // Живое «сейчас»: отсчёт «через N мин» обновляется сам
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val next = Timeline.next(entries, now)

    // Строки ленты: шапка, подписи дней и пункты — чтобы знать индексы для «барабана»
    val rows = remember(entries, now / 60_000) {
        buildList<PlanRow> {
            add(PlanRow.Header)
            var lastDay = ""
            entries.forEachIndexed { i, e ->
                val day = Format.day(e.at, e.zone)
                if (day != lastDay) {
                    lastDay = day
                    add(PlanRow.Day(day, e.at < now))
                }
                add(PlanRow.Item(e, i))
            }
        }
    }
    val nextRow = rows.indexOfFirst { it is PlanRow.Item && it.entry === next }.let { i ->
        if (i > 0 && rows[i - 1] is PlanRow.Day) i - 1 else i
    }
    // Открываем план на ближайшем событии: прошедшие уже «укатились» в барабан сверху
    val state = rememberLazyListState()
    // Данные приходят из базы асинхронно — перематываем, когда план построен (один раз)
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(nextRow) {
        if (!scrolled && nextRow > 2) {
            state.scrollToItem(nextRow - 2)
            scrolled = true
        }
    }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state, rows) {
        snapshotFlow { state.firstVisibleItemIndex }.drop(1).collect { i ->
            val row = rows.getOrNull(i)
            if (row is PlanRow.Item && row.entry.at < now || row is PlanRow.Day && row.past) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Start),
    ) {
        itemsIndexed(rows, key = { i, r -> if (r is PlanRow.Item) "e${r.index}" else "r$i" }) { i, row ->
            when (row) {
                PlanRow.Header -> Box(Modifier.drum(state, i)) { Appear(0, "header") { TripHeader(c, flights, stays, now) } }
                is PlanRow.Day -> Box(Modifier.drum(state, i, row.past)) {
                    SectionLabel(row.label, Modifier.padding(start = 6.dp, top = 18.dp, bottom = 12.dp))
                }
                is PlanRow.Item -> {
                    val e = row.entry
                    Box(Modifier.drum(state, i, e.at < now)) {
                        Appear(row.index.coerceAtMost(8), "entry-${row.index}") {
                            Column {
                                if (e === next) {
                                    Text(
                                        "Следующее · ${Format.countdown(e.at, now)}", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                                    )
                                }
                                TimelineRow(
                                    e, c, { onEditFlight(it.id) }, { onEditStay(it.id) },
                                    first = row.index == 0, last = row.index == entries.lastIndex, highlight = e === next, now = now,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("План пока пуст", style = MaterialTheme.typography.titleLarge)
                    Text("Нажмите «Документы» и выберите билеты и брони — план соберётся сам.")
                }
            }
        }
    }
}

private sealed interface PlanRow {
    data object Header : PlanRow
    data class Day(val label: String, val past: Boolean) : PlanRow
    data class Item(val entry: TimelineEntry, val index: Int) : PlanRow
}

/**
 * Эффект барабана, как в выборе времени на iPhone: у верхнего края пункт поворачивается
 * от зрителя, уменьшается и гаснет. Применяется к прошедшим событиям (и шапке).
 */
@Composable
private fun Modifier.drum(state: LazyListState, index: Int, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current.density
    return this.graphicsLayer {
        val layout = state.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
        val zone = layout.viewportSize.height * 0.34f
        if (zone <= 0f) return@graphicsLayer
        val center = info.offset + info.size / 2f
        val d = ((zone - center) / zone).coerceIn(0f, 1f)
        rotationX = 68f * d
        val scale = 1f - 0.16f * d
        scaleX = scale
        scaleY = scale
        alpha = 1f - 0.8f * d
        transformOrigin = TransformOrigin(0.5f, 1f)
        cameraDistance = 10f * density
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
                "FLIGHT" -> Icons.Outlined.Flight to com.travelplanner.app.ui.theme.TripColors.Flight
                "TRAIN" -> Icons.Outlined.Train to com.travelplanner.app.ui.theme.TripColors.Train
                "HOTEL" -> Icons.Outlined.Hotel to com.travelplanner.app.ui.theme.TripColors.Hotel
                else -> Icons.Outlined.Description to com.travelplanner.app.ui.theme.TripColors.Checkout
            }
            Panel(onClick = { openDocument(context, File(d.path)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(22.dp), tint = color)
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
