package com.travelplanner.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData

sealed class TimelineEntry(val at: Long, val zone: String, val tripId: Long) {
    class GoToAirport(val flight: Flight) : TimelineEntry(flight.departureMillis - flight.remindMinutes * 60_000L, flight.departureZone, flight.tripId)
    class FlightEntry(val flight: Flight) : TimelineEntry(flight.departureMillis, flight.departureZone, flight.tripId)
    class Transfer(val flight: Flight, val stay: Stay) : TimelineEntry((flight.arrivalMillis ?: flight.departureMillis) + 1, flight.arrivalZone, flight.tripId)
    class CheckIn(val stay: Stay) : TimelineEntry(stay.checkInMillis, stay.zone, stay.tripId)
    class CheckOut(val stay: Stay) : TimelineEntry(stay.checkOutMillis, stay.zone, stay.tripId)
}

object Timeline {
    fun build(flights: List<Flight>, stays: List<Stay>): List<TimelineEntry> {
        val list = mutableListOf<TimelineEntry>()
        flights.forEach { f ->
            list += TimelineEntry.GoToAirport(f)
            list += TimelineEntry.FlightEntry(f)
            val arrival = f.arrivalMillis
            if (arrival != null) {
                // Едем из аэропорта в жильё, если заселение в течение суток после прилёта (или уже идёт)
                stays.firstOrNull { s ->
                    s.tripId == f.tripId &&
                        ((s.checkInMillis >= arrival - 3_600_000L && s.checkInMillis - arrival < 24 * 3_600_000L) ||
                            (s.checkInMillis <= arrival && s.checkOutMillis > arrival))
                }?.let { list += TimelineEntry.Transfer(f, it) }
            }
        }
        stays.forEach { s ->
            list += TimelineEntry.CheckIn(s)
            list += TimelineEntry.CheckOut(s)
        }
        return list.sortedBy { it.at }
    }

    fun next(entries: List<TimelineEntry>, now: Long = System.currentTimeMillis()): TimelineEntry? =
        entries.firstOrNull { it.at > now }
}

fun openMaps(context: Context, query: String) {
    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + Uri.encode(query))))
    }
}

fun dial(context: Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
}

fun airportQuery(ref: ReferenceData, iata: String): String =
    ref.airport(iata)?.let { "Аэропорт ${it.name} ${it.city} ${it.iata}" } ?: "Airport $iata"

@Composable
fun TimelineCard(
    entry: TimelineEntry,
    ref: ReferenceData,
    onEditFlight: (Flight) -> Unit,
    onEditStay: (Stay) -> Unit,
    highlight: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    val context = LocalContext.current
    val past = entry.at < now
    val colors = if (highlight) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else CardDefaults.cardColors()
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (past && !highlight) 0.5f else 1f),
        colors = colors,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (entry) {
                is TimelineEntry.GoToAirport -> {
                    val f = entry.flight
                    if (f.isTrain) {
                        Header(Icons.Default.DirectionsCar, Format.time(entry.at, entry.zone), "Выезд на вокзал")
                        Text(f.fromName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${f.title()} отправляется в ${Format.time(f.departureMillis, f.departureZone)} · за ${Format.duration(f.remindMinutes * 60_000L)} до отправления",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        val airport = ref.airport(f.fromIata)
                        Header(Icons.Default.DirectionsCar, Format.time(entry.at, entry.zone), "Выезд в аэропорт")
                        Text(
                            (airport?.title ?: f.fromIata) + (f.departureTerminal.takeIf { it.isNotBlank() }?.let { ", терминал $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Вылет ${f.title()} в ${Format.time(f.departureMillis, f.departureZone)} · за ${Format.duration(f.remindMinutes * 60_000L)} до вылета",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        ref.airline(f.airlineCode)?.let {
                            Text("Регистрация в аэропорту закрывается за ${it.airportCheckinCloseMinutes} мин", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Chips {
                        ActionChip(Icons.Default.Map, "Маршрут") { openMaps(context, f.departurePlaceQuery(ref)) }
                    }
                }
                is TimelineEntry.FlightEntry -> {
                    val f = entry.flight
                    val airline = if (f.isTrain) null else ref.airline(f.airlineCode)
                    Column(Modifier.clickable { onEditFlight(f) }) {
                        Header(
                            if (f.isTrain) Icons.Default.Train else Icons.Default.Flight,
                            Format.time(f.departureMillis, f.departureZone),
                            f.title() + (airline?.let { " · ${it.name}" } ?: ""),
                        )
                        val arrival = f.arrivalMillis
                        Text("${f.fromLabel(ref)} → ${f.toLabel(ref)}", style = MaterialTheme.typography.bodyLarge)
                        if (arrival != null) {
                            val sameDay = Format.shortDate(arrival, f.arrivalZone) == Format.shortDate(f.departureMillis, f.departureZone)
                            Text(
                                (if (f.isTrain) "Прибытие " else "Прилёт ") +
                                    (if (sameDay) "" else Format.shortDate(arrival, f.arrivalZone) + " ") +
                                    "в ${Format.time(arrival, f.arrivalZone)} по местному · в пути ${Format.duration(arrival - f.departureMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        val details = listOfNotNull(
                            f.departureTerminal.takeIf { it.isNotBlank() }?.let { "Терминал $it" },
                            f.car.takeIf { it.isNotBlank() }?.let { "Вагон $it" },
                            f.seat.takeIf { it.isNotBlank() }?.let { "Место $it" },
                            f.bookingRef.takeIf { it.isNotBlank() }?.let { (if (f.isTrain) "Заказ " else "Бронь ") + it },
                        )
                        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (Format.utcOffset(f.departureZone) != Format.utcOffset(f.arrivalZone)) {
                            Text(
                                "Смена часового пояса: ${Format.utcOffset(f.departureZone)} → ${Format.utcOffset(f.arrivalZone)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                is TimelineEntry.Transfer -> {
                    val s = entry.stay
                    Header(
                        if (entry.flight.isTrain) Icons.Default.Train else Icons.Default.FlightLand,
                        Format.time(entry.at, entry.zone),
                        (if (entry.flight.isTrain) "С вокзала" else "Из аэропорта") + " — в «${s.name}»",
                    )
                    if (s.address.isNotBlank()) Text(s.address, style = MaterialTheme.typography.bodyMedium)
                    Text("Заселение с ${Format.time(s.checkInMillis, s.zone)}", style = MaterialTheme.typography.bodySmall)
                    Chips {
                        if (s.address.isNotBlank()) ActionChip(Icons.Default.Map, "Маршрут") { openMaps(context, s.address) }
                    }
                }
                is TimelineEntry.CheckIn -> {
                    val s = entry.stay
                    Column(Modifier.clickable { onEditStay(s) }) {
                        Header(Icons.Default.Hotel, Format.time(s.checkInMillis, s.zone), "Заселение: ${s.name}")
                        if (s.address.isNotBlank()) Text(s.address, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "До ${Format.dateTime(s.checkOutMillis, s.zone)}" + (s.bookingRef.takeIf { it.isNotBlank() }?.let { " · бронь $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Chips {
                        if (s.address.isNotBlank()) ActionChip(Icons.Default.Map, "На карте") { openMaps(context, s.address) }
                        if (s.phone.isNotBlank()) ActionChip(Icons.Default.Phone, "Позвонить") { dial(context, s.phone) }
                    }
                }
                is TimelineEntry.CheckOut -> {
                    val s = entry.stay
                    Column(Modifier.clickable { onEditStay(s) }) {
                        Header(Icons.AutoMirrored.Filled.Logout, Format.time(s.checkOutMillis, s.zone), "Выезд: ${s.name}")
                        Text("Освободить номер до ${Format.time(s.checkOutMillis, s.zone)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, time: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
