package com.travelplanner.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.travelplanner.app.AppContainer
import com.travelplanner.app.reference.AppInfo

/**
 * Такси и общественный транспорт: открывает самое популярное в стране приложение такси
 * (если можно — сразу с маршрутом), иначе Google Play; транспорт — Яндекс Карты (СНГ) или Google Карты.
 */
object Taxi {

    fun isInstalled(context: Context, pkg: String): Boolean = pkg.isNotBlank() && runCatching {
        context.packageManager.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)

    /** Есть ли на телефоне сервисы Google (на Huawei/Honor последних лет их нет). */
    fun hasGoogleServices(context: Context): Boolean = isInstalled(context, "com.google.android.gms")

    fun openApp(context: Context, app: AppInfo) {
        val launch = context.packageManager.getLaunchIntentForPackage(app.androidPackage)
        if (launch != null) context.startActivity(launch) else openStore(context, app)
    }

    fun openStore(context: Context, app: AppInfo) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.androidPackage}"))
        runCatching { context.startActivity(market) }.onFailure { openUrl(context, app.googlePlayUrl) }
    }

    /**
     * Вызов такси. [from]/[to] — текстовые адреса (аэропорт, вокзал, отель); [country] — где заказываем.
     */
    suspend fun order(context: Context, c: AppContainer, country: String?, from: String?, to: String?) {
        val app = c.appGuide.taxiFor(country) ?: return
        val pkg = app.androidPackage
        val a = from?.let { c.travelTime.locate(it) }
        val b = to?.let { c.travelTime.locate(it) }
        when {
            pkg == "ru.yandex.taxi" && isInstalled(context, pkg) && b != null -> {
                val uri = buildString {
                    append("yandextaxi://route?")
                    if (a != null) append("start-lat=${a.first}&start-lon=${a.second}&")
                    append("end-lat=${b.first}&end-lon=${b.second}&appmetrica_tracking_id=1178268795219780156")
                }
                start(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri))) { openApp(context, app) }
            }
            pkg == "com.ubercab" && b != null -> {
                val uri = buildString {
                    append("https://m.uber.com/ul/?action=setPickup")
                    if (a != null) append("&pickup[latitude]=${a.first}&pickup[longitude]=${a.second}") else append("&pickup=my_location")
                    append("&dropoff[latitude]=${b.first}&dropoff[longitude]=${b.second}")
                    append("&dropoff[nickname]=" + Uri.encode(to))
                }
                start(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri))) { openApp(context, app) }
            }
            isInstalled(context, pkg) -> {
                // Маршрут передать нельзя — копируем адрес, чтобы вставить его в поле «Куда»
                if (to != null) {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("address", to))
                    Toast.makeText(context, "Адрес скопирован — вставьте его в поле «Куда» в ${app.name}", Toast.LENGTH_LONG).show()
                }
                openApp(context, app)
            }
            else -> {
                Toast.makeText(context, "${app.name} — самое популярное такси здесь. Установите приложение", Toast.LENGTH_LONG).show()
                openStore(context, app)
            }
        }
    }

    /** Общественный транспорт: Яндекс Карты (Россия и СНГ), иначе Google Карты. */
    suspend fun transit(context: Context, c: AppContainer, country: String?, from: String?, to: String) {
        if (c.appGuide.isRussianSpace(country) && isInstalled(context, "ru.yandex.yandexmaps")) {
            val a = from?.let { c.travelTime.locate(it) }
            val b = c.travelTime.locate(to)
            if (b != null) {
                val rtext = (a?.let { "${it.first},${it.second}" } ?: "") + "~${b.first},${b.second}"
                val ok = start(context, Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?rtext=$rtext&rtt=mt"))) {}
                if (ok) return
            }
        }
        val url = "https://www.google.com/maps/dir/?api=1" +
            (from?.let { "&origin=" + Uri.encode(it) } ?: "") +
            "&destination=" + Uri.encode(to) + "&travelmode=transit"
        openUrl(context, url)
    }

    private fun start(context: Context, intent: Intent, fallback: () -> Unit): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrElse { fallback(); false }

    fun hasPackage(pm: PackageManager, pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
}
