package com.travelplanner.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightEditScreen(c: AppContainer, tripId: Long, flightId: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var form by remember { mutableStateOf<FlightForm?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flightId) {
        form = if (flightId == 0L) FlightForm(remindHours = c.settings.remindHours)
        else c.db.flights().get(flightId)?.let { FlightForm.of(it) } ?: FlightForm(remindHours = c.settings.remindHours)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (flightId == 0L) "Новый перелёт" else "Перелёт") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (flightId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                },
            )
        },
    ) { padding ->
        val f = form ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlightFormFields(f, c.ref)
            Text("Напомнить «пора в аэропорт» за:", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4, 5).forEach { h ->
                    FilterChip(selected = f.remindHours == h, onClick = { f.remindHours = h }, label = { Text("$h ч") })
                }
            }
            f.base?.attachmentPath?.let { path ->
                OutlinedButton(onClick = { openDocument(context, File(path)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null)
                    Text("  Открыть билет")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val flight = f.toFlight(tripId, c.ref)
                    if (flight == null) {
                        error = "Заполните: " + f.missing().joinToString(", ")
                    } else {
                        scope.launch {
                            c.repository.saveFlight(flight)
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить перелёт?") },
            text = { Text("Напоминания и событие в календаре тоже будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    form?.base?.let { b -> scope.launch { c.repository.deleteFlight(b); onDone() } }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayEditScreen(c: AppContainer, tripId: Long, stayId: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val flights by c.repository.flights(tripId).collectAsState(initial = emptyList())
    var form by remember { mutableStateOf<StayForm?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attachment by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        error = null
        info = null
        scope.launch {
            runCatching { c.importer.import(uri) }
                .onSuccess { r ->
                    attachment = r.storedFile.absolutePath
                    val p = r.document.stay
                    val f = form
                    if (p == null || f == null) {
                        error = "Не удалось найти данные брони в файле. Файл прикреплён — заполните поля вручную."
                    } else {
                        p.name?.let { f.name = it }
                        p.address?.let { f.address = it }
                        f.checkInDate = p.checkIn
                        f.checkInTime = p.checkInTime
                        p.checkOut?.let { f.checkOutDate = it }
                        f.checkOutTime = p.checkOutTime
                        p.phone?.let { f.phone = it }
                        r.document.bookingRef?.let { f.booking = it }
                        info = "Данные брони заполнены из файла — проверьте их и нажмите «Сохранить»."
                    }
                }
                .onFailure { error = "Ошибка чтения файла: ${it.message}" }
            loading = false
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(stayId) {
        form = if (stayId == 0L) StayForm() else c.db.stays().get(stayId)?.let { StayForm.of(it) } ?: StayForm()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (stayId == 0L) "Новое жильё" else "Жильё") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (stayId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                },
            )
        },
    ) { padding ->
        val s = form ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*", "text/plain", "text/html")) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.UploadFile, null)
                Text(if (loading) "  Распознаю бронь…" else "  Загрузить бронь из файла (PDF, скриншот)")
            }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            StayFormFields(s)
            s.checkInDate?.let {
                Text(
                    "Время — местное, часовой пояс: ${StayForm.zoneFor(it, flights)} (по рейсу прилёта)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            (attachment ?: s.base?.attachmentPath)?.let { path ->
                OutlinedButton(onClick = { openDocument(context, File(path)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null)
                    Text("  Открыть бронь")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val stay = s.toStay(tripId, flights, attachment ?: s.base?.attachmentPath)
                    if (stay == null) {
                        error = "Заполните: " + s.missing().joinToString(", ")
                    } else {
                        scope.launch {
                            c.repository.saveStay(stay)
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить жильё?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    form?.base?.let { b -> scope.launch { c.repository.deleteStay(b); onDone() } }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
