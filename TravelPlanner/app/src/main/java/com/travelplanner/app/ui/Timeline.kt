package com.travelplanner.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Itinerary
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData
import com.travelplanner.app.ui.theme.TripColors
import kotlinx.coroutines.launch

sealed class TimelineEntry(val at: Long, val zone: String, val tripId: Long) {
    /** Выезд в аэропорт/на вокзал; [fromStay] — отель, из которого выезжаем (если есть). */
    class GoToAirport(val flight: Flight, val fromStay: Stay? = null) :
        TimelineEntry(flight.departureMillis - flight.remindMinutes * 60_000L, flight.departureZone, flight.tripId)

    /** Переезд; [arrivalActions] — после прибытия показать «Такси / Транспорт» (если дальше не пересадка и не отель). */
    class FlightEntry(val flight: Flight, val arrivalActions: Boolean = false) :
        TimelineEntry(flight.departureMillis, flight.departureZone, flight.tripId)

    /** Пересадка: прилетели и улетаем из того же аэропорта — ехать никуда не нужно. */
    class Layover(val flight: Flight, val prev: Flight) : TimelineEntry(prev.arrivalMillis!! + 1, flight.departureZone, flight.tripId)
    class Transfer(val flight: Flight, val stay: Stay) :
        TimelineEntry((flight.arrivalMillis ?: flight.departureMillis) + 1, flight.arrivalZone, flight.tripId)

    /** Заселение показываем не раньше прилёта в город отеля. */
    class CheckIn(val stay: Stay, at: Long = stay.checkInMillis) : TimelineEntry(at, stay.zone, stay.tripId)

    /** Выезд из отеля: раньше расчётного часа, если до него нужно ехать на вокзал/в аэропорт. */
    class CheckOut(val stay: Stay, at: Long = stay.checkOutMillis) : TimelineEntry(at, stay.zone, stay.tripId)
}

object Timeline {
    fun build(flights: List<Flight>, stays: List<Stay>, ref: ReferenceData): List<TimelineEntry> {
        val it = Itinerary(ref)
        val list = mutableListOf<TimelineEntry>()
        val transferFlights = stays.mapNotNull { s -> it.arrivalToStay(s, flights, stays)?.let { f -> f.id to s } }.toMap()
        val connectionStarts = flights.mapNotNull { f -> it.connectionFrom(f, flights, stays)?.id }.toSet()
        flights.forEach { f ->
            val prev = it.connectionFrom(f, flights, stays)
            list += if (prev != null) {
                TimelineEntry.Layover(f, prev)
            } else {
                TimelineEntry.GoToAirport(f, it.stayBefore(f, stays))
            }
            list += TimelineEntry.FlightEntry(f, arrivalActions = f.arrivalMillis != null && f.id !in connectionStarts && f.id !in transferFlights)
        }
        stays.forEach { s ->
            val arrival = it.arrivalToStay(s, flights, stays)
            if (arrival != null) list += TimelineEntry.Transfer(arrival, s)
            val at = arrival?.arrivalMillis?.takeIf { a -> a > s.checkInMillis }?.plus(2) ?: s.checkInMillis
            list += TimelineEntry.CheckIn(s, at)
            list += TimelineEntry.CheckOut(s, it.leaveStayAt(s, flights, stays) ?: s.checkOutMillis)
        }
        return list.sortedBy { e -> e.at }
    }

    fun next(entries: List<TimelineEntry>, now: Long = System.currentTimeMillis()): TimelineEntry? =
        entries.firstOrNull { it.at > now }
}

fun openMaps(context: Context, query: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
    runCatching { context.startActivity(intent) }.onFailure {
        openUrl(context, "https://maps.google.com/?q=" + Uri.encode(query))
    }
}

fun dial(context: Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
}

fun airportQuery(ref: ReferenceData, iata: String): String =
    ref.airport(iata)?.let { "Аэропорт ${it.name} ${it.city} ${it.iata}" } ?: "Airport $iata"

fun Stay.placeQuery(): String = "$name, $address".trim(',', ' ')
fun Flight.arrivalPlaceQuery(ref: ReferenceData): String = if (isTrain) "вокзал $toName" else airportQuery(ref, toIata)

/** Цвет и иконка пункта маршрута. */
private fun style(e: TimelineEntry): Pair<Color, ImageVector> = when (e) {
    is TimelineEntry.GoToAirport -> TripColors.Road to Icons.Default.DirectionsCar
    is TimelineEntry.FlightEntry -> if (e.flight.isTrain) TripColors.Train to Icons.Default.Train else TripColors.Flight to Icons.Default.Flight
    is TimelineEntry.Layover -> TripColors.Layover to Icons.Default.SyncAlt
    is TimelineEntry.Transfer -> TripColors.Road to Icons.Default.LocalTaxi
    is TimelineEntry.CheckIn -> TripColors.Hotel to Icons.Default.Hotel
    is TimelineEntry.CheckOut -> TripColors.Checkout to Icons.AutoMirrored.Filled.Logout
}

/** Строка ленты: слева линия маршрута с цветным значком, справа карточка. */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    c: AppContainer,
    onEditFlight: (Flight) -> Unit,
    onEditStay: (Stay) -> Unit,
    first: Boolean,
    last: Boolean,
    highlight: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    val (color, icon) = style(entry)
    val past = entry.at < now && !highlight
    val lineColor = color.copy(alpha = 0.35f)
    // Линию рисуем фоном строки — без измерения «внутренней» высоты карточки
    Row(
        Modifier.fillMaxWidth().alpha(if (past) 0.55f else 1f).drawBehind {
            val x = 22.dp.toPx()
            val iconCenter = 31.dp.toPx()
            drawLine(
                lineColor, Offset(x, if (first) iconCenter else 0f), Offset(x, if (last) iconCenter else size.height),
                strokeWidth = 3.dp.toPx(),
            )
        },
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(34.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f).padding(bottom = 10.dp)) {
            TimelineCard(entry, c, onEditFlight, onEditStay, highlight, now, color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineCard(
    entry: TimelineEntry,
    c: AppContainer,
    onEditFlight: (Flight) -> Unit,
    onEditStay: (Stay) -> Unit,
    highlight: Boolean = false,
    now: Long = System.currentTimeMillis(),
    accent: Color = style(entry).first,
) {
    val ref = c.ref
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun taxi(country: String?, from: String?, to: String?) = scope.launch { Taxi.order(context, c, country, from, to) }
    fun transit(country: String?, from: String?, to: String) = scope.launch { Taxi.transit(context, c, country, from, to) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = if (highlight) CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.16f)) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (entry) {
                is TimelineEntry.GoToAirport -> {
                    val f = entry.flight
                    val country = c.itinerary.departureCountry(f)
                    val target = f.departurePlaceQuery(ref)
                    Title(Format.time(entry.at, entry.zone), if (f.isTrain) "Выезд на вокзал" else "Выезд в аэропорт", accent)
                    if (f.isTrain) {
                        Text(f.fromName, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            (ref.airport(f.fromIata)?.title ?: f.fromIata) + (f.departureTerminal.takeIf { it.isNotBlank() }?.let { ", терминал $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "${f.title()} в ${Format.time(f.departureMillis, f.departureZone)} · выезд за ${Format.duration(f.remindMinutes * 60_000L)}" +
                            (entry.fromStay?.let { " из «${it.name}»" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!f.isTrain) ref.airline(f.airlineCode)?.let {
                        Text("Регистрация закрывается за ${it.airportCheckinCloseMinutes} мин до вылета", style = MaterialTheme.typography.bodySmall)
                    }
                    Actions {
                        ActionChip(Icons.Default.LocalTaxi, "Такси") { taxi(country, entry.fromStay?.placeQuery(), target) }
                        ActionChip(Icons.Default.DirectionsBus, "Транспорт") { transit(country, entry.fromStay?.placeQuery(), target) }
                        ActionChip(Icons.Default.Map, "Карта") { openMaps(context, target) }
                    }
                }
                is TimelineEntry.FlightEntry -> {
                    val f = entry.flight
                    val airline = if (f.isTrain) null else ref.airline(f.airlineCode)
                    Column(Modifier.clickable { onEditFlight(f) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Title(Format.time(f.departureMillis, f.departureZone), f.title() + (airline?.let { " · ${it.name}" } ?: ""), accent)
                        Text("${f.fromLabel(ref)} → ${f.toLabel(ref)}", style = MaterialTheme.typography.titleSmall)
                        f.arrivalMillis?.let { arrival ->
                            val sameDay = Format.shortDate(arrival, f.arrivalZone) == Format.shortDate(f.departureMillis, f.departureZone)
                            Text(
                                (if (f.isTrain) "Прибытие " else "Прилёт ") + (if (sameDay) "" else Format.shortDate(arrival, f.arrivalZone) + " ") +
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
                                "🕒 Смена часового пояса: ${Format.utcOffset(f.departureZone)} → ${Format.utcOffset(f.arrivalZone)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    val country = c.itinerary.arrivalCountry(f)
                    Actions {
                        if (!f.isTrain) ActionChip(Icons.Default.Radar, "Статус рейса") {
                            openUrl(context, "https://www.flightradar24.com/data/flights/${(f.airlineCode + f.flightNumber).lowercase()}")
                        }
                        if (entry.arrivalActions) {
                            ActionChip(Icons.Default.LocalTaxi, "Такси") { taxi(country, f.arrivalPlaceQuery(ref), null) }
                            ActionChip(Icons.Default.DirectionsBus, "Транспорт") {
                                transit(country, f.arrivalPlaceQuery(ref), "центр ${c.itinerary.arrivalCity(f)}")
                            }
                        }
                    }
                }
                is TimelineEntry.Layover -> {
                    val f = entry.flight
                    val airport = ref.airport(f.fromIata)
                    Title(Format.time(entry.at, entry.zone), "Пересадка: ${airport?.city ?: f.fromIata}", accent)
                    Text(
                        "${Format.duration(f.departureMillis - entry.prev.arrivalMillis!!)} до вылета ${f.title()} в ${Format.time(f.departureMillis, f.departureZone)}" +
                            (f.departureTerminal.takeIf { t -> t.isNotBlank() }?.let { t -> ", терминал $t" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Из аэропорта выходить не нужно — следите за табло и выходом на посадку", style = MaterialTheme.typography.bodySmall)
                }
                is TimelineEntry.Transfer -> {
                    val s = entry.stay
                    val f = entry.flight
                    val country = c.itinerary.arrivalCountry(f)
                    Title(Format.time(entry.at, entry.zone), (if (f.isTrain) "С вокзала" else "Из аэропорта") + " — в «${s.name}»", accent)
                    if (s.address.isNotBlank()) Text(s.address, style = MaterialTheme.typography.bodyMedium)
                    Text("Заселение с ${Format.time(s.checkInMillis, s.zone)}", style = MaterialTheme.typography.bodySmall)
                    c.appGuide.taxiFor(country)?.let { Text("Такси здесь: ${it.name}", style = MaterialTheme.typography.bodySmall) }
                    Actions {
                        ActionChip(Icons.Default.LocalTaxi, "Такси") { taxi(country, f.arrivalPlaceQuery(ref), s.placeQuery()) }
                        ActionChip(Icons.Default.DirectionsBus, "Транспорт") { transit(country, f.arrivalPlaceQuery(ref), s.placeQuery()) }
                        ActionChip(Icons.AutoMirrored.Filled.DirectionsWalk, "Маршрут") { openMaps(context, s.placeQuery()) }
                    }
                }
                is TimelineEntry.CheckIn -> {
                    val s = entry.stay
                    Column(Modifier.clickable { onEditStay(s) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Title(Format.time(entry.at, s.zone), "Заселение: ${s.name}", accent)
                        if (entry.at != s.checkInMillis) {
                            Text("Заселение возможно с ${Format.time(s.checkInMillis, s.zone)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.address.isNotBlank()) Text(s.address, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "До ${Format.dateTime(s.checkOutMillis, s.zone)}" + (s.bookingRef.takeIf { it.isNotBlank() }?.let { " · бронь $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Actions {
                        ActionChip(Icons.Default.Map, "На карте") { openMaps(context, s.placeQuery()) }
                        if (s.phone.isNotBlank()) ActionChip(Icons.Default.Phone, "Позвонить") { dial(context, s.phone) }
                    }
                }
                is TimelineEntry.CheckOut -> {
                    val s = entry.stay
                    Column(Modifier.clickable { onEditStay(s) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Title(Format.time(entry.at, s.zone), "Выезд: ${s.name}", accent)
                        Text(
                            if (entry.at < s.checkOutMillis) "Расчётный час — ${Format.time(s.checkOutMillis, s.zone)}, но выезжать нужно раньше. Не забудьте вещи и сдать ключи."
                            else "Освободить номер до ${Format.time(s.checkOutMillis, s.zone)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(time: String, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
