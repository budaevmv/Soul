package com.travelplanner.app.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Оценка дороги от отеля до вокзала по координатам (геокодер Android).
 * Координаты кешируются: без интернета работают уже найденные адреса.
 */
class TravelTime(private val context: Context) {
    private val prefs = context.getSharedPreferences("geo_cache", Context.MODE_PRIVATE)

    data class Estimate(val km: Double, val minutes: Int, val walk: Boolean)

    suspend fun locate(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        prefs.getString(key, null)?.let { v ->
            if (v == "-") return@withContext null
            val (lat, lon) = v.split(',').map { it.toDouble() }
            return@withContext lat to lon
        }
        if (!Geocoder.isPresent()) return@withContext null
        val found = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.forLanguageTag("ru")).getFromLocationName(query, 1)?.firstOrNull()
        }.getOrNull()
        if (found != null) {
            prefs.edit().putString(key, "${found.latitude},${found.longitude}").apply()
            found.latitude to found.longitude
        } else {
            null // не кешируем неудачу: возможно, просто нет интернета
        }
    }

    suspend fun estimate(from: String, to: String): Estimate? {
        val a = locate(from) ?: return null
        val b = locate(to) ?: return null
        val km = distanceKm(a, b)
        if (km > 80) return null // геокодер ошибся городом
        val walk = km <= 1.5
        val minutes = if (walk) (km / 4.5 * 60 * 1.3).roundToInt() + 3 else (km * 1.3 / 25 * 60).roundToInt() + 15
        return Estimate(km, minutes, walk)
    }

    companion object {
        fun distanceKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val r = 6371.0
            val dLat = Math.toRadians(b.first - a.first)
            val dLon = Math.toRadians(b.second - a.second)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }

        /** Запас на вокзале: прийти за 30 минут до отправления. */
        const val STATION_BUFFER_MIN = 30
    }
}
