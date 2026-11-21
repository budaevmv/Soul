package com.travelplanner.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.importer.AttentionItem
import com.travelplanner.app.importer.FileResult
import com.travelplanner.app.importer.ImportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Типы файлов, которые можно выбрать в «Проводнике». */
val DOCUMENT_TYPES = arrayOf("application/pdf", "image/*", "text/plain", "text/html", "message/rfc822")

/**
 * Загрузка документов: выбрали сразу много файлов — приложение само поняло, что это
 * (перелёт / поезд / отель), разложило по поездкам, добавило в календарь и поставило напоминания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(
    c: AppContainer,
    tripId: Long,
    initialUris: List<Uri>,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onFixFlight: (Long) -> Unit,
    onFixStay: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<ImportReport?>(null) }
    var started by rememberSaveable { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }

    fun run(uris: List<Uri>) {
        if (uris.isEmpty()) return
        running = true
        total = uris.size
        scope.launch {
            val r = c.assembler.importAll(uris, tripId.takeIf { it > 0 }) { d, t, name ->
                done = d
                total = t
                current = name
            }
            report = r
            running = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty() && report == null) onBack() else run(uris)
    }
    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        if (initialUris.isNotEmpty()) run(initialUris) else picker.launch(DOCUMENT_TYPES)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Загрузка документов") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (running) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Распознаю документы: ${minOf(done + 1, total)} из $total", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        if (current.isNotBlank()) Text(current, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val r = report
            if (r == null && !running) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Выберите сразу все билеты и брони (можно отметить несколько файлов). " +
                                "Приложение само определит, где перелёт, поезд или отель, и соберёт поездку.",
                        )
                        Button(onClick = { picker.launch(DOCUMENT_TYPES) }) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Выбрать файлы")
                        }
                        OutlinedButton(onClick = { pasteDialog = true }) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Вставить текст письма")
                        }
                    }
                }
            }
            if (r != null) {
                item { Summary(r, c, onOpenTrip) }
                if (r.attention.isNotEmpty()) {
                    item { Text("Нужно уточнить", style = MaterialTheme.typography.titleMedium) }
                    items(r.attention) { a ->
                        AttentionCard(a) {
                            when {
                                a.stay != null -> {
                                    c.draftStay = StayForm.of(a.stay, a.booking).also { it.draftAttachment = a.attachment }
                                    onFixStay(a.tripId)
                                }
                                a.train != null -> {
                                    c.draftFlight = FlightForm.of(a.train, a.booking, a.passenger, c.settings.trainRemindMinutes)
                                        .also { it.draftAttachment = a.attachment }
                                    onFixFlight(a.tripId)
                                }
                                a.flight != null -> {
                                    c.draftFlight = FlightForm.of(a.flight, a.booking, a.passenger, c.settings.remindHours)
                                        .also { it.draftAttachment = a.attachment }
                                    onFixFlight(a.tripId)
                                }
                            }
                        }
                    }
                }
                item { Text("Файлы", style = MaterialTheme.typography.titleMedium) }
                items(r.files) { f -> FileRow(f) }
                item {
                    OutlinedButton(onClick = { report = null; picker.launch(DOCUMENT_TYPES) }) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Загрузить ещё")
                    }
                }
            }
        }
    }

    if (pasteDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasteDialog = false },
            title = { Text("Текст брони или билета") },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    pasteDialog = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { c.importer.saveText(text) }
                        run(listOf(Uri.fromFile(file)))
                    }
                }) { Text("Распознать") }
            },
            dismissButton = { TextButton(onClick = { pasteDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Summary(r: ImportReport, c: AppContainer, onOpenTrip: (Long) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        r.added > 0 -> "Добавлено пунктов: ${r.added}"
                        r.duplicates > 0 -> "Всё из этих файлов уже есть в поездках"
                        else -> "Новых пунктов не найдено"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
            }
            if (r.duplicates > 0) Text("Пропущено дубликатов: ${r.duplicates}", style = MaterialTheme.typography.bodyMedium)
            if (r.added > 0) {
                Text(
                    if (c.settings.calendarSync && c.calendar.hasPermission()) "Всё добавлено в календарь, напоминания установлены."
                    else "Напоминания установлены. Чтобы пункты попали в календарь, разрешите доступ к календарю.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            r.trips.forEach { (id, title) ->
                Button(onClick = { onOpenTrip(id) }, modifier = Modifier.fillMaxWidth()) { Text("Открыть поездку «$title»") }
            }
        }
    }
}

@Composable
private fun AttentionCard(a: AttentionItem, onFix: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.titleSmall)
                Text("Не найдено: ${a.missing.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onFix) { Text("Дополнить") }
        }
    }
}

@Composable
private fun FileRow(f: FileResult) {
    val (icon: ImageVector, label) = when (f.kind) {
        FileResult.Kind.FLIGHT -> Icons.Default.Flight to "Авиабилет"
        FileResult.Kind.TRAIN -> Icons.Default.Train to "ЖД-билет"
        FileResult.Kind.HOTEL -> Icons.Default.Hotel to "Бронь жилья"
        FileResult.Kind.MIXED -> Icons.Default.Description to "Билеты и брони"
        FileResult.Kind.DUPLICATE -> Icons.Default.ContentCopy to "Дубликат"
        FileResult.Kind.UNKNOWN -> Icons.Default.Description to "Документ"
        FileResult.Kind.ERROR -> Icons.Default.ErrorOutline to "Ошибка"
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$label · ${f.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(f.summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
