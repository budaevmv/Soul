package com.travelplanner.app.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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

    LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (noGms && abroad) item { Appear(0) { NoGoogleCard() } }
        item { Appear(1) { PaymentCard(c, countries, abroad) } }
        itemsIndexed(countries) { i, code ->
            val info = c.ref.country(code)
            val apps = c.appGuide.forCountry(code)
            Appear(i + 2, code) {
                Panel {
                    SectionLabel("приложения")
                    Text(info?.name ?: code, style = MaterialTheme.typography.headlineSmall)
                    Group("Такси", apps.taxi, c)
                    Group("Карты", apps.maps, c)
                    Group("Транспорт", apps.transport, c)
                    Group("Оплата", apps.pay, c)
                    Group("Связь", apps.other, c)
                }
            }
        }
        item {
            Appear(countries.size + 2, "always") {
                Panel {
                    SectionLabel("в любой поездке")
                    Text("Пригодится везде", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Скачайте заранее, пока есть быстрый интернет: офлайн-карты городов и языки переводчика.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
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
    SectionLabel(title, Modifier.padding(top = 12.dp, bottom = 2.dp))
    apps.forEach { AppRow(it) }
}

/** Строка приложения: настоящая иконка (если установлено) или монограмма, без цветных плашек. */
@Composable
fun AppRow(app: AppInfo) {
    val context = LocalContext.current
    val installed = remember(app) { Taxi.isInstalled(context, app.androidPackage) }
    val icon = remember(app, installed) {
        if (!installed) null else runCatching {
            context.packageManager.getApplicationIcon(app.androidPackage).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(app.name.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleSmall)
            Text(app.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "App Store", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).pressable { openUrl(context, app.appStoreUrl) },
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill(null, if (installed) "Открыть" else "Установить", { if (installed) Taxi.openApp(context, app) else Taxi.openStore(context, app) }, filled = installed)
    }
}

@Composable
private fun PaymentCard(c: AppContainer, countries: List<String>, abroad: Boolean) {
    Panel {
        SectionLabel("деньги")
        Text("Как платить", style = MaterialTheme.typography.headlineSmall)
        countries.filter { it != "RU" }.forEach { code ->
            c.ref.country(code)?.let { info ->
                if (info.money.isNotBlank()) {
                    Text(info.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(info.money, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (abroad) {
            Spacer(Modifier.height(8.dp))
            Bullets(
                listOf(
                    "Российские Visa и Mastercard за рубежом не работают, «Мир» — лишь в нескольких странах. Основное — наличные USD/EUR новыми купюрами.",
                    "Карты UnionPay российских банков принимают в Китае и частично в Азии и на Ближнем Востоке — проверьте заранее.",
                    "Привяжите карты к такси и кошелькам дома и проверьте оплату: SMS-коды приходят на российский номер — держите его в роуминге.",
                ),
            )
        }
    }
}

@Composable
private fun NoGoogleCard() {
    Panel(color = MaterialTheme.colorScheme.errorContainer) {
        SectionLabel("важно")
        Text(
            "На ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} нет сервисов Google",
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Bullets(
            listOf(
                "Не работают Google Pay и Google Карты; Grab, Uber, Careem, Alipay и многие кошельки могут не запускаться или не привязывать карту.",
                "Возьмите пластиковую карту и наличные — не рассчитывайте только на телефон.",
                "Лучше второй телефон с сервисами Google: поставьте на него такси, карты и оплату и проверьте вход ещё дома.",
                "Без Google работают Яндекс Go, Яндекс Карты, 2ГИС, Organic Maps, Яндекс Переводчик — ставьте их из AppGallery или RuStore.",
            ),
        )
    }
}
