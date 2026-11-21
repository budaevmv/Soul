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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.reference.AirlineInfo
import com.travelplanner.app.reference.CountryInfo
import com.travelplanner.app.reference.ReferenceData

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun Bullets(items: List<String>, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = tint)
                } else {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(6.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AirlineCard(code: String, info: AirlineInfo?, expandedByDefault: Boolean = true) {
    val context = LocalContext.current
    var expanded by rememberSaveable(code) { mutableStateOf(expandedByDefault) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(info?.name ?: "Авиакомпания $code", style = MaterialTheme.typography.titleLarge)
                    Text("Код IATA: $code", style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (!expanded) return@Column
            if (info == null) {
                Text(
                    "Этой авиакомпании пока нет в справочнике. Действуют общие правила ниже — нормы ручной клади и багажа уточните на сайте перевозчика.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            LabeledText("Ручная кладь", info.hand)
            LabeledText("Багаж", info.bag)
            LabeledText(
                "Регистрация",
                buildString {
                    if (info.onlineCheckinHours > 0) append("Онлайн — открывается за ${info.onlineCheckinHours} ч до вылета. ")
                    append("В аэропорту закрывается за ${info.airportCheckinCloseMinutes} мин.")
                },
            )
            if (info.notes.isNotEmpty()) Bullets(info.notes, Icons.Default.Info, MaterialTheme.colorScheme.primary)
            if (info.web.isNotBlank()) {
                TextButton(onClick = { openUrl(context, info.web) }) {
                    Icon(Icons.Default.Language, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Сайт авиакомпании")
                }
            }
            Text(
                "Нормы зависят от тарифа и меняются — перед поездкой сверьтесь с билетом и сайтом.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun GeneralRulesCard() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Что нельзя везти: общие правила", style = MaterialTheme.typography.titleMedium)
            SectionTitle("Ручная кладь")
            Bullets(ReferenceData.generalCabinRules, Icons.Default.Block, MaterialTheme.colorScheme.error)
            SectionTitle("Багаж")
            Bullets(ReferenceData.generalBaggageRules, Icons.Default.Block, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun CountryCard(code: String, info: CountryInfo?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${info?.flag ?: ""} ${info?.name ?: code}".trim(), style = MaterialTheme.typography.headlineSmall)
            if (info == null) {
                Text("Для этой страны пока нет справки. Проверьте визовые и таможенные правила на сайте МИД и консульства.")
                return@Column
            }
            LabeledText("Въезд", info.visa)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { LabeledText("Валюта", info.currency) }
                Column(Modifier.weight(1f)) { LabeledText("Розетки", info.plug) }
            }
            LabeledText("Экстренные службы", info.sos)
            LabeledText("Деньги и карты", info.money)
            HorizontalDivider()
            SectionTitle("📶 Связь и интернет")
            Text(info.connectivity, style = MaterialTheme.typography.bodyMedium)
            val prohibited = info.prohibited + if (info.schengen) ReferenceData.schengenCommon else emptyList()
            if (prohibited.isNotEmpty()) {
                SectionTitle("⛔ Нельзя ввозить / вывозить")
                Bullets(prohibited, Icons.Default.Block, MaterialTheme.colorScheme.error)
            }
            if (info.take.isNotEmpty()) {
                SectionTitle("🎒 Лучше взять с собой")
                Bullets(info.take, Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondary)
            }
            if (info.tips.isNotEmpty()) {
                SectionTitle("💡 Полезно знать")
                Bullets(info.tips)
            }
            Text(
                "Правила въезда и таможни меняются. Перед поездкой проверьте актуальную информацию на сайте МИД / консульства.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Чек-лист сборов: отметки сохраняются на устройстве для каждой поездки. */
@Composable
fun PackingChecklist(tripId: Long, items: List<String>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("checklist", Context.MODE_PRIVATE) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Чек-лист сборов", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            items.forEach { item ->
                val key = "$tripId:${item.hashCode()}"
                var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        checked = !checked
                        prefs.edit().putBoolean(key, checked).apply()
                    }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = {
                        checked = it
                        prefs.edit().putBoolean(key, it).apply()
                    })
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
