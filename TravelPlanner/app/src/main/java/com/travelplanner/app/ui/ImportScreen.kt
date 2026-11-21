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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.importer.ExtractedContent
import com.travelplanner.app.importer.ParsedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    c: AppContainer,
    tripId: Long,
    initialUri: Uri?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trips by c.repository.trips().collectAsState(initial = emptyList())
    var targetTrip by remember { mutableStateOf<Long?>(tripId.takeIf { it > 0 }) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<ParsedDocument?>(null) }
    var storedPath by remember { mutableStateOf<String?>(null) }
    var rawText by remember { mutableStateOf("") }
    val flightForms = remember { mutableStateListOf<FlightForm>() }
    var stayForm by remember { mutableStateOf<StayForm?>(null) }
    var pasteMode by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    fun apply(doc: ParsedDocument) {
        parsed = doc
        flightForms.clear()
        doc.flights.forEach { flightForms += FlightForm.of(it, doc.bookingRef, doc.passenger, c.settings.remindHours) }
        stayForm = doc.stay?.let { StayForm.of(it, doc.bookingRef) }
        if (doc.isEmpty) error = "Не удалось найти рейсы или бронь в документе. Можно ввести данные вручную."
    }

    fun importUri(uri: Uri) {
        loading = true
        error = null
        scope.launch {
            runCatching { c.importer.import(uri) }
                .onSuccess {
                    storedPath = it.storedFile.absolutePath
                    rawText = it.rawText
                    apply(it.document)
                }
                .onFailure { error = "Ошибка чтения файла: ${it.message}" }
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importUri(it) } }
    LaunchedEffect(initialUri) { initialUri?.let { importUri(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Загрузка билета") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Поддерживаются PDF маршрутных квитанций, скриншоты и фото билетов, посадочные со штрихкодом, брони отелей. " +
                    "Распознавание выполняется на телефоне, данные никуда не отправляются.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*", "text/plain", "text/html", "message/rfc822")) }) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Выбрать файл")
                }
                OutlinedButton(onClick = { pasteMode = !pasteMode }) {
                    Icon(Icons.Default.ContentPaste, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Вставить текст")
                }
            }
            if (pasteMode) {
                OutlinedTextField(
                    pasted, { pasted = it },
                    label = { Text("Текст письма с бронью / билета") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                Button(enabled = pasted.isNotBlank(), onClick = {
                    error = null
                    rawText = pasted
                    scope.launch {
                        storedPath = withContext(Dispatchers.IO) { c.importer.saveText(pasted).absolutePath }
                        apply(c.importer.parse(ExtractedContent(pasted, emptyList())))
                        pasteMode = false
                    }
                }) { Text("Распознать") }
            }

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text("Распознаю документ…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (parsed != null) {
                if (tripId <= 0) {
                    Text("Добавить в поездку:", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = targetTrip == null, onClick = { targetTrip = null }, label = { Text("Новая поездка") })
                        trips.take(8).forEach { t ->
                            FilterChip(selected = targetTrip == t.id, onClick = { targetTrip = t.id }, label = { Text(t.title) })
                        }
                    }
                }

                flightForms.forEachIndexed { i, form ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(form.selected, { form.selected = it })
                                Icon(Icons.Default.Flight, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Рейс ${i + 1}: ${form.airline} ${form.number}" + (c.ref.airline(form.airline)?.let { " · ${it.name}" } ?: ""),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            if (form.selected) {
                                FlightFormFields(form, c.ref, compact = true)
                                val missing = form.missing()
                                if (missing.isNotEmpty()) {
                                    Text("Не распознано: ${missing.joinToString()} — укажите вручную", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                stayForm?.let { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(s.selected, { s.selected = it })
                                Icon(Icons.Default.Hotel, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Проживание", style = MaterialTheme.typography.titleMedium)
                            }
                            if (s.selected) StayFormFields(s)
                        }
                    }
                }
                val doc = parsed!!
                if (doc.flights.isEmpty() && doc.stay == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { flightForms += FlightForm(remindHours = c.settings.remindHours) }) { Text("+ Перелёт вручную") }
                        OutlinedButton(onClick = { stayForm = StayForm() }) { Text("+ Жильё вручную") }
                    }
                }
                if (flightForms.isNotEmpty() || stayForm != null) {
                    Button(
                        onClick = {
                            val selectedFlights = flightForms.filter { it.selected }
                            val bad = selectedFlights.firstOrNull { it.missing().isNotEmpty() }
                            val stay = stayForm?.takeIf { it.selected }
                            if (bad != null) {
                                error = "Рейс ${bad.airline} ${bad.number}: заполните ${bad.missing().joinToString()}"
                                return@Button
                            }
                            if (stay != null && stay.missing().isNotEmpty()) {
                                error = "Жильё: заполните ${stay.missing().joinToString()}"
                                return@Button
                            }
                            scope.launch {
                                val id = targetTrip ?: c.repository.createTrip(autoTitle(c, selectedFlights, stay))
                                selectedFlights.forEach { f -> f.toFlight(id, c.ref, storedPath)?.let { c.repository.saveFlight(it) } }
                                if (stay != null) {
                                    val flights = c.db.flights().forTrip(id)
                                    stay.toStay(id, flights, storedPath)?.let { c.repository.saveStay(it) }
                                }
                                onSaved(id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Сохранить в поездку") }
                }
                if (rawText.isNotBlank()) {
                    var showRaw by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Скрыть распознанный текст" else "Показать распознанный текст") }
                    if (showRaw) Text(rawText.take(4000), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun autoTitle(c: AppContainer, flights: List<FlightForm>, stay: StayForm?): String {
    val dest = flights.firstOrNull()?.to?.let { c.ref.cityName(it) }
    val date = flights.firstOrNull()?.depDate ?: stay?.checkInDate
    val month = date?.let { java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale.forLanguageTag("ru")).format(it) }
    return listOfNotNull(dest ?: stay?.name, month).joinToString(", ").ifBlank { "Новая поездка" }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
