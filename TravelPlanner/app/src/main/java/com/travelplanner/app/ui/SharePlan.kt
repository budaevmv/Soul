package com.travelplanner.app.ui

import android.content.Context
import android.content.Intent
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay

/** Отправить план поездки текстом (близким, в мессенджер). */
fun sharePlan(context: Context, c: AppContainer, title: String, flights: List<Flight>, stays: List<Stay>) {
    val ref = c.ref
    val text = buildString {
        appendLine("🧳 $title")
        var lastDay = ""
        Timeline.build(flights, stays, ref).forEach { e ->
            val day = Format.day(e.at, e.zone)
            if (day != lastDay) {
                lastDay = day
                appendLine()
                appendLine("📅 $day")
            }
            val t = Format.time(e.at, e.zone)
            val line = when (e) {
                is TimelineEntry.GoToAirport -> null
                is TimelineEntry.FlightEntry -> {
                    val f = e.flight
                    val arr = f.arrivalMillis?.let { " → ${Format.time(it, f.arrivalZone)}" } ?: ""
                    (if (f.isTrain) "🚆" else "✈️") + " $t$arr ${f.title()} ${f.fromLabel(ref)} → ${f.toLabel(ref)}"
                }
                is TimelineEntry.Layover -> "🔁 $t пересадка в ${ref.airport(e.flight.fromIata)?.city ?: e.flight.fromIata}"
                is TimelineEntry.Transfer -> null
                is TimelineEntry.CheckIn -> "🏨 $t заселение: ${e.stay.name}" + (e.stay.address.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "") +
                    (e.stay.phone.takeIf { it.isNotBlank() }?.let { ", тел. $it" } ?: "")
                is TimelineEntry.CheckOut -> "🚪 $t выезд из «${e.stay.name}»"
            }
            line?.let { appendLine(it) }
        }
        appendLine()
        append("Время местное. Отправлено из приложения «Мои поездки».")
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Поделиться планом"))
}
