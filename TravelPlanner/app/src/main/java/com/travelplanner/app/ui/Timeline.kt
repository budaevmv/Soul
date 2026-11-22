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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Train
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
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
@Composable
private fun style(e: TimelineEntry): Pair<Color, ImageVector> = when (e) {
    is TimelineEntry.GoToAirport -> TripColors.Road to Icons.Outlined.DirectionsCar
    is TimelineEntry.FlightEntry -> if (e.flight.isTrain) TripColors.Train to Icons.Outlined.Train else TripColors.Flight to Icons.Outlined.Flight
    is TimelineEntry.Layover -> TripColors.Layover to Icons.Outlined.SyncAlt
    is TimelineEntry.Transfer -> TripColors.Road to Icons.Outlined.LocalTaxi
    is TimelineEntry.CheckIn -> TripColors.Hotel to Icons.Outlined.Hotel
    is TimelineEntry.CheckOut -> TripColors.Checkout to Icons.AutoMirrored.Outlined.Logout
}

private fun isMajor(e: TimelineEntry) = e is TimelineEntry.FlightEntry || e is TimelineEntry.CheckIn

/** Строка ленты: тонкая линия маршрута с иконкой без рамки, справа — содержимое. */
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
    val major = isMajor(entry)
    val lineColor = MaterialTheme.colorScheme.outline
    val iconTop = if (major) 22.dp else 4.dp
    Row(
        Modifier.fillMaxWidth().alpha(if (past) 0.45f else 1f).drawBehind {
            val x = 16.dp.toPx()
            val gapTop = iconTop.toPx() - 6.dp.toPx()
            val gapBottom = iconTop.toPx() + 26.dp.toPx()
            val stroke = 1.dp.toPx()
            if (!first) drawLine(lineColor, Offset(x, 0f), Offset(x, gapTop), strokeWidth = stroke)
            if (!last) drawLine(lineColor, Offset(x, gapBottom), Offset(x, size.height), strokeWidth = stroke)
        },
    ) {
        Box(Modifier.width(32.dp).padding(top = iconTop), contentAlignment = Alignment.TopCenter) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(bottom = if (major) 14.dp else 18.dp)) {
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val panelColor = if (highlight) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer

    when (entry) {
        is TimelineEntry.GoToAirport -> Minor {
            val f = entry.flight
            val country = c.itinerary.departureCountry(f)
            val target = f.departurePlaceQuery(ref)
            MinorTitle(Format.time(entry.at, entry.zone), if (f.isTrain) "Выезд на вокзал" else "Выезд в аэропорт", accent, highlight)
            Text(
                if (f.isTrain) f.fromName
                else (ref.airport(f.fromIata)?.title ?: f.fromIata) + (f.departureTerminal.takeIf { it.isNotBlank() }?.let { ", терминал $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "За ${Format.duration(f.remindMinutes * 60_000L)} до отправления" + (entry.fromStay?.let { " · из «${it.name}»" } ?: "") +
                    (if (!f.isTrain) ref.airline(f.airlineCode)?.let { " · регистрация до −${it.airportCheckinCloseMinutes} мин" } ?: "" else ""),
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
            Actions {
                Pill(Icons.Outlined.LocalTaxi, "Такси", { taxi(country, entry.fromStay?.placeQuery(), target) })
                Pill(Icons.Outlined.DirectionsBus, "Транспорт", { transit(country, entry.fromStay?.placeQuery(), target) })
                Pill(Icons.Outlined.Map, "Карта", { openMaps(context, target) })
            }
        }
        is TimelineEntry.FlightEntry -> {
            val f = entry.flight
            val airline = if (f.isTrain) null else ref.airline(f.airlineCode)
            Panel(onClick = { onEditFlight(f) }, color = panelColor) {
                BoardingPass(f, ref, accent)
                Text(
                    listOfNotNull(
                        f.title(), airline?.name,
                        f.arrivalMillis?.let { "в пути ${Format.duration(it - f.departureMillis)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
                val details = listOfNotNull(
                    f.departureTerminal.takeIf { it.isNotBlank() }?.let { "Терминал $it" },
                    f.car.takeIf { it.isNotBlank() }?.let { "Вагон $it" },
                    f.seat.takeIf { it.isNotBlank() }?.let { "Место $it" },
                    f.bookingRef.takeIf { it.isNotBlank() }?.let { (if (f.isTrain) "Заказ " else "Бронь ") + it },
                )
                if (details.isNotEmpty()) Text(details.joinToString("   "), style = MaterialTheme.typography.labelMedium)
                if (Format.utcOffset(f.departureZone) != Format.utcOffset(f.arrivalZone)) {
                    Text(
                        "Часовой пояс ${Format.utcOffset(f.departureZone)} → ${Format.utcOffset(f.arrivalZone)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                val country = c.itinerary.arrivalCountry(f)
                Actions {
                    if (!f.isTrain) Pill(Icons.Outlined.Radar, "Статус", {
                        openUrl(context, "https://www.flightradar24.com/data/flights/${(f.airlineCode + f.flightNumber).lowercase()}")
                    })
                    if (entry.arrivalActions) {
                        Pill(Icons.Outlined.LocalTaxi, "Такси", { taxi(country, f.arrivalPlaceQuery(ref), null) })
                        Pill(Icons.Outlined.DirectionsBus, "Транспорт", { transit(country, f.arrivalPlaceQuery(ref), "центр ${c.itinerary.arrivalCity(f)}") })
                    }
                }
            }
        }
        is TimelineEntry.Layover -> Minor {
            val f = entry.flight
            MinorTitle(Format.time(entry.at, entry.zone), "Пересадка · ${ref.airport(f.fromIata)?.city ?: f.fromIata}", accent, highlight)
            Text(
                "${Format.duration(f.departureMillis - entry.prev.arrivalMillis!!)} до вылета ${f.title()}" +
                    (f.departureTerminal.takeIf { t -> t.isNotBlank() }?.let { t -> " · терминал $t" } ?: "") +
                    ". Аэропорт покидать не нужно.",
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
        }
        is TimelineEntry.Transfer -> Minor {
            val s = entry.stay
            val f = entry.flight
            val country = c.itinerary.arrivalCountry(f)
            MinorTitle(Format.time(entry.at, entry.zone), "В «${s.name}»", accent, highlight)
            Text(
                listOfNotNull(
                    s.address.takeIf { it.isNotBlank() },
                    c.appGuide.taxiFor(country)?.let { "такси здесь — ${it.name}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
            Actions {
                Pill(Icons.Outlined.LocalTaxi, "Такси", { taxi(country, f.arrivalPlaceQuery(ref), s.placeQuery()) }, filled = true)
                Pill(Icons.Outlined.DirectionsBus, "Транспорт", { transit(country, f.arrivalPlaceQuery(ref), s.placeQuery()) })
                Pill(Icons.AutoMirrored.Outlined.DirectionsWalk, "Маршрут", { openMaps(context, s.placeQuery()) })
            }
        }
        is TimelineEntry.CheckIn -> {
            val s = entry.stay
            Panel(onClick = { onEditStay(s) }, color = panelColor) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Format.time(entry.at, s.zone), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, color = accent)
                    Spacer(Modifier.width(10.dp))
                    SectionLabel("заселение", Modifier.padding(bottom = 4.dp))
                }
                Text(s.name, style = MaterialTheme.typography.titleLarge)
                if (s.address.isNotBlank()) Text(s.address, style = MaterialTheme.typography.bodyMedium, color = muted)
                Text(
                    listOfNotNull(
                        if (entry.at != s.checkInMillis) "заселение с ${Format.time(s.checkInMillis, s.zone)}" else null,
                        "до ${Format.dateTime(s.checkOutMillis, s.zone)}",
                        s.bookingRef.takeIf { it.isNotBlank() }?.let { "бронь $it" },
                    ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                )
                Actions {
                    Pill(Icons.Outlined.Map, "На карте", { openMaps(context, s.placeQuery()) })
                    if (s.phone.isNotBlank()) Pill(Icons.Outlined.Phone, "Позвонить", { dial(context, s.phone) })
                }
            }
        }
        is TimelineEntry.CheckOut -> Minor(onClick = { onEditStay(entry.stay) }) {
            val s = entry.stay
            MinorTitle(Format.time(entry.at, s.zone), "Выезд из «${s.name}»", accent, highlight)
            Text(
                if (entry.at < s.checkOutMillis) "Расчётный час ${Format.time(s.checkOutMillis, s.zone)}, но выехать нужно раньше — к отправлению."
                else "Освободить номер до ${Format.time(s.checkOutMillis, s.zone)}",
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
        }
    }
}

/** Посадочный талон: крупное время и коды/станции, между ними тонкая линия с самолётом/поездом. */
@Composable
private fun BoardingPass(f: Flight, ref: ReferenceData, accent: Color) {
    val arrival = f.arrivalMillis
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Format.time(f.departureMillis, f.departureZone), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
            Text(if (f.isTrain) f.fromName else f.fromIata, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(f.fromCity(ref), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(0.7f).height(24.dp), contentAlignment = Alignment.Center) {
            val line = MaterialTheme.colorScheme.outline
            Box(Modifier.fillMaxWidth().height(1.dp).background(line))
            Icon(
                if (f.isTrain) Icons.Outlined.Train else Icons.Outlined.Flight, null,
                Modifier.size(18.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 2.dp).rotate(if (f.isTrain) 0f else 90f),
                tint = accent,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(arrival?.let { Format.time(it, f.arrivalZone) } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
            Text(if (f.isTrain) f.toName else f.toIata, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.End)
            Text(
                f.toCity(ref) + (arrival?.let { a -> if (Format.shortDate(a, f.arrivalZone) != Format.shortDate(f.departureMillis, f.departureZone)) ", ${Format.shortDate(a, f.arrivalZone)}" else "" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun Minor(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().pressable(onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun MinorTitle(time: String, title: String, accent: Color, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.titleMedium, color = if (highlight) accent else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(content: @Composable () -> Unit) {
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
