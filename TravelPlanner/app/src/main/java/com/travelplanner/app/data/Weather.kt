package com.travelplanner.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

@Serializable
data class WeatherDay(
    val date: String,
    val tMax: Double,
    val tMin: Double,
    /** Вероятность осадков, % (для прогноза) */
    val rainChance: Int? = null,
    /** Осадки, мм (для «обычной погоды» по прошлому году) */
    val rainMm: Double? = null,
    val code: Int? = null,
) {
    val rainy: Boolean get() = (rainChance ?: 0) >= 50 || (rainMm ?: 0.0) >= 3.0 || code in 51..99
}

/** Погода в городе на даты пребывания. forecast=false — «как обычно в эти даты» (прошлый год). */
@Serializable
data class StopWeather(val city: String, val from: String, val to: String, val days: List<WeatherDay>, val forecast: Boolean) {
    val minT: Double get() = days.minOfOrNull { it.tMin } ?: 0.0
    val maxT: Double get() = days.maxOfOrNull { it.tMax } ?: 0.0
    val rainyDays: Int get() = days.count { it.rainy }
}

/**
 * Погода через Open-Meteo (бесплатно, без ключа). Прогноз — на 16 дней вперёд;
 * для более далёких дат — фактическая погода в эти же дни год назад.
 */
class WeatherService(context: Context) {
    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forStop(city: String, englishHint: String?, from: LocalDate, to: LocalDate): StopWeather? = withContext(Dispatchers.IO) {
        val key = "$city|$from|$to"
        prefs.getString(key, null)?.let { cached ->
            val (ts, body) = cached.split('\n', limit = 2)
            if (System.currentTimeMillis() - ts.toLong() < 3 * 3_600_000L) {
                return@withContext runCatching { json.decodeFromString<StopWeather>(body) }.getOrNull()
            }
        }
        val loc = geocode(city) ?: englishHint?.let { geocode(it) } ?: return@withContext null
        val today = LocalDate.now()
        val forecast = !to.isBefore(today) && !from.isAfter(today.plusDays(15))
        val days = if (forecast) {
            val start = maxOf(from, today)
            val end = minOf(to, today.plusDays(15))
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&timezone=auto" +
                "&start_date=$start&end_date=$end"
            parseDaily(get(url) ?: return@withContext null, forecast = true)
        } else {
            val start = from.minusYears(1)
            val end = to.minusYears(1)
            val url = "https://archive-api.open-meteo.com/v1/archive?latitude=${loc.first}&longitude=${loc.second}" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code&timezone=auto" +
                "&start_date=$start&end_date=$end"
            parseDaily(get(url) ?: return@withContext null, forecast = false)
        }
        if (days.isEmpty()) return@withContext null
        val result = StopWeather(city, from.toString(), to.toString(), days, forecast)
        prefs.edit().putString(key, "${System.currentTimeMillis()}\n${json.encodeToString(result)}").apply()
        result
    }

    private fun geocode(name: String): Pair<Double, Double>? {
        val cacheKey = "geo|$name"
        prefs.getString(cacheKey, null)?.let { v -> v.split(',').let { return it[0].toDouble() to it[1].toDouble() } }
        val body = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name=" + URLEncoder.encode(name, "UTF-8")) ?: return null
        val first = runCatching { json.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull() ?: return null
        val lat = first["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        prefs.edit().putString(cacheKey, "$lat,$lon").apply()
        return lat to lon
    }

    private fun parseDaily(body: String, forecast: Boolean): List<WeatherDay> = runCatching {
        val daily = json.parseToJsonElement(body).jsonObject["daily"] as JsonObject
        fun arr(n: String) = daily[n] as? JsonArray
        val dates = arr("time")!!.map { it.jsonPrimitive.content }
        dates.indices.mapNotNull { i ->
            val max = arr("temperature_2m_max")?.get(i)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val min = arr("temperature_2m_min")?.get(i)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            WeatherDay(
                date = dates[i], tMax = max, tMin = min,
                rainChance = if (forecast) arr("precipitation_probability_max")?.get(i)?.jsonPrimitive?.intOrNull else null,
                rainMm = if (!forecast) arr("precipitation_sum")?.get(i)?.jsonPrimitive?.doubleOrNull else null,
                code = arr("weather_code")?.get(i)?.jsonPrimitive?.intOrNull,
            )
        }
    }.getOrDefault(emptyList())

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    companion object {
        /** Код погоды WMO -> эмодзи и описание. */
        fun describe(code: Int?): Pair<String, String> = when (code) {
            0 -> "☀️" to "ясно"
            1, 2 -> "🌤" to "переменная облачность"
            3 -> "☁️" to "пасмурно"
            45, 48 -> "🌫" to "туман"
            in 51..57 -> "🌦" to "морось"
            in 61..67, in 80..82 -> "🌧" to "дождь"
            in 71..77, 85, 86 -> "🌨" to "снег"
            in 95..99 -> "⛈" to "гроза"
            else -> "🌡" to ""
        }
    }
}
