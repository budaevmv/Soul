package com.travelplanner.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.AppInfo

private val APP_COLORS = listOf(
    Color(0xFF2F7CF6), Color(0xFF16A34A), Color(0xFF9333EA), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF0EA5E9), Color(0xFFDB2777),
)

/** Приложения для поездки: такси, карты, транспорт, оплата — по каждой стране, со ссылками на магазины. */
@Composable
fun AppsTab(c: AppContainer, flights: List<Flight>, stays: List<Stay>) {
    val context = LocalContext.current
    val noGms = remember { !Taxi.hasGoogleServices(context) }
    val stops = remember(flights, stays) { c.itinerary.stops(flights, stays) }
    val countries = (stops.mapNotNull { it.country } +
        flights.sortedBy { it.departureMillis }.mapNotNull { c.itinerary.departureCountry(it) }).distinct()
    val abroad = countries.any { it != "RU" }

    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PaymentCard(c, countries, noGms, abroad) }
        items(countries) { code ->
            val info = c.ref.country(code)
            val apps = c.appGuide.forCountry(code)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${info?.flag ?: "🌍"} ${info?.name ?: code}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Group("🚕 Такси", apps.taxi, c)
                    Group("🗺 Карты", apps.maps, c)
                    Group("🚌 Транспорт", apps.transport, c)
                    Group("💳 Оплата", apps.pay, c)
                    Group("💬 Связь", apps.other, c)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🧳 Пригодится в любой поездке", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Скачайте заранее, пока есть быстрый интернет: офлайн-карты городов и языки переводчика.", style = MaterialTheme.typography.bodySmall)
                    c.appGuide.always().forEach { AppRow(it) }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, keys: List<String>, c: AppContainer) {
    val apps = keys.mapNotNull { c.appGuide.app(it) }
    if (apps.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    apps.forEach { AppRow(it) }
}

@Composable
fun AppRow(app: AppInfo) {
    val context = LocalContext.current
    val installed = remember(app) { Taxi.isInstalled(context, app.androidPackage) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = APP_COLORS[Math.floorMod(app.name.hashCode(), APP_COLORS.size)]
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(app.name.take(1), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleSmall)
            Text(app.description, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { openUrl(context, app.appStoreUrl) }, contentPadding = PaddingValues(0.dp)) { Text("App Store (iPhone)") }
        }
        FilledTonalButton(onClick = { if (installed) Taxi.openApp(context, app) else Taxi.openStore(context, app) }) {
            Text(if (installed) "Открыть" else "Google Play")
        }
    }
}

@Composable
private fun PaymentCard(c: AppContainer, countries: List<String>, noGms: Boolean, abroad: Boolean) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (noGms && abroad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("💳 Как платить", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            countries.filter { it != "RU" }.forEach { code ->
                c.ref.country(code)?.let { info ->
                    if (info.money.isNotBlank()) Text("${info.flag} ${info.name}: ${info.money}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (abroad) {
                Bullets(
                    listOf(
                        "Российские Visa и Mastercard за рубежом не работают, «Мир» — лишь в нескольких странах. Основной вариант — наличные USD/EUR новыми купюрами.",
                        "Карты UnionPay российских банков принимают в Китае и частично в Азии и на Ближнем Востоке — проверьте заранее.",
                        "Привяжите карты к приложениям такси и кошелькам ДОМА и проверьте оплату: SMS-коды приходят на российский номер — держите его в роуминге.",
                    ),
                )
            }
            if (noGms) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "На этом телефоне (${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}) нет сервисов Google",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        )
                        Bullets(
                            listOf(
                                "Не работают Google Pay и Google Карты; Grab, Uber, Careem, Alipay и многие кошельки могут не запускаться или не привязывать карту.",
                                "Возьмите пластиковую карту и наличные — не рассчитывайте только на телефон.",
                                "Лучше взять второй телефон с сервисами Google: установите на него такси, карты и оплату и проверьте вход ещё дома.",
                                "Без Google работают: Яндекс Go, Яндекс Карты, 2ГИС, Organic Maps, Яндекс Переводчик. Приложения ставьте из AppGallery или RuStore.",
                            ),
                        )
                    }
                }
            }
        }
    }
}
