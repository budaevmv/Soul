package com.travelplanner.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.data.Trip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    c: AppContainer,
    onOpenTrip: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trips by c.repository.trips().collectAsState(initial = emptyList())
    val flights by c.repository.allFlights().collectAsState(initial = emptyList())
    val stays by c.repository.allStays().collectAsState(initial = emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showNewTrip by remember { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(c.scheduler.canScheduleExact()) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.WRITE_CALENDAR] == true) {
            scope.launch { c.repository.rescheduleAll(syncCalendar = true) }
        }
    }
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (c.settings.calendarSync && !c.calendar.hasPermission()) {
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
            }
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }

    val entries = remember(flights, stays) { Timeline.build(flights, stays) }
    val next = Timeline.next(entries, now)
    val tripTitles = trips.associate { it.id to it.title }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои поездки") },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Настройки") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Default.UploadFile, null) },
                text = { Text("Загрузить документы") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!exactAllowed && flights.any { it.departureMillis > now }) {
                item {
                    WarningCard(
                        "Разрешите точные будильники, чтобы напоминание «пора в аэропорт» пришло вовремя.",
                        "Разрешить",
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                            )
                        }
                        exactAllowed = true
                    }
                }
            }
            if (next != null) {
                item {
                    Text("Ближайшее · ${Format.countdown(next.at, now)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(tripTitles[next.tripId] ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(Format.day(next.at, next.zone), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    TimelineCard(
                        next, c.ref,
                        onEditFlight = { onOpenTrip(it.tripId) },
                        onEditStay = { onOpenTrip(it.tripId) },
                        highlight = true, now = now,
                    )
                }
            }
            if (trips.isEmpty()) {
                item { EmptyState() }
            } else {
                item { Text("Поездки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(trips, key = { it.id }) { trip ->
                    TripCard(trip, flights.filter { it.tripId == trip.id }, stays.filter { it.tripId == trip.id }, c, now) { onOpenTrip(trip.id) }
                }
            }
        }
    }

    if (showNewTrip) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTrip = false },
            title = { Text("Новая поездка") },
            text = { OutlinedTextField(title, { title = it }, label = { Text("Название, например «Стамбул, ноябрь»") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    showNewTrip = false
                    scope.launch { onOpenTrip(c.repository.createTrip(title)) }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showNewTrip = false }) { Text("Отмена") } },
        )
    }

    if (showSettings) {
        SettingsDialog(
            c,
            onDismiss = { showSettings = false },
            onSaved = { scope.launch { c.repository.rescheduleAll() } },
            onCalendarEnabled = {
                permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            },
        )
    }
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Пока нет поездок", style = MaterialTheme.typography.titleLarge)
            Text(
                "Нажмите «Загрузить документы» и отметьте сразу все файлы: авиабилеты, ЖД-билеты, брони отелей " +
                    "(PDF, скриншоты, фото). Приложение само поймёт, что где, соберёт поездку с маршрутом по дням, " +
                    "добавит всё в календарь и напомнит, когда выезжать в аэропорт или на вокзал.\n\n" +
                    "Можно также «Поделиться» файлами из почты или мессенджера прямо в это приложение.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TripCard(trip: Trip, flights: List<Flight>, stays: List<Stay>, c: AppContainer, now: Long, onClick: () -> Unit) {
    val starts = (flights.map { it.departureMillis to it.departureZone } + stays.map { it.checkInMillis to it.zone }).minByOrNull { it.first }
    val ends = (flights.map { (it.arrivalMillis ?: it.departureMillis) to it.arrivalZone } + stays.map { it.checkOutMillis to it.zone }).maxByOrNull { it.first }
    val route = flights.sortedBy { it.departureMillis }.let { fs ->
        if (fs.isEmpty()) "" else (listOf(fs.first().fromCity(c.ref)) + fs.map { it.toCity(c.ref) })
            .fold(mutableListOf<String>()) { acc, s -> if (acc.lastOrNull() != s) acc += s; acc }.joinToString(" → ")
    }
    val finished = ends != null && ends.first < now
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (finished) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleLarge)
            if (starts != null && ends != null) {
                Text(
                    "${Format.shortDate(starts.first, starts.second)} — ${Format.shortDate(ends.first, ends.second)}" + if (finished) " · завершена" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (route.isNotBlank()) Text(route, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text("Перелётов: ${flights.size} · Жильё: ${stays.size}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WarningCard(text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null)
            Spacer(Modifier.width(8.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SettingsDialog(c: AppContainer, onDismiss: () -> Unit, onSaved: () -> Unit, onCalendarEnabled: () -> Unit) {
    var hours by remember { mutableStateOf(c.settings.remindHours) }
    var calendar by remember { mutableStateOf(c.settings.calendarSync) }
    var checkin by remember { mutableStateOf(c.settings.checkinReminder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Напоминать «пора в аэропорт» за (для новых перелётов):")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4, 5).forEach { h ->
                        FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("$h ч") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Добавлять в календарь телефона", Modifier.weight(1f))
                    Switch(calendar, { calendar = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Напоминать об онлайн-регистрации", Modifier.weight(1f))
                    Switch(checkin, { checkin = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.settings.remindHours = hours
                val enabledCalendar = calendar && !c.settings.calendarSync
                c.settings.calendarSync = calendar
                c.settings.checkinReminder = checkin
                if (enabledCalendar) onCalendarEnabled()
                onSaved()
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
