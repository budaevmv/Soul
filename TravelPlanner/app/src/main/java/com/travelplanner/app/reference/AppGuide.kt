package com.travelplanner.app.reference

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppInfo(
    @SerialName("n") val name: String,
    @SerialName("d") val description: String = "",
    @SerialName("pkg") val androidPackage: String = "",
    /** id в App Store (пусто — ссылка на поиск). */
    val ios: String = "",
) {
    val googlePlayUrl: String get() = "https://play.google.com/store/apps/details?id=$androidPackage"
    val appStoreUrl: String
        get() = if (ios.isNotBlank()) "https://apps.apple.com/app/id$ios"
        else "https://www.google.com/search?q=" + java.net.URLEncoder.encode("$name App Store", "UTF-8")
}

@Serializable
data class CountryApps(
    val taxi: List<String> = emptyList(),
    val maps: List<String> = emptyList(),
    val transport: List<String> = emptyList(),
    val pay: List<String> = emptyList(),
    val other: List<String> = emptyList(),
)

@Serializable
private data class AppGuideJson(
    val apps: Map<String, AppInfo>,
    val countries: Map<String, CountryApps>,
    val default: CountryApps,
    val always: List<String>,
)

/** Какие приложения нужны в стране: такси, карты, транспорт, оплата, связь. */
class AppGuide private constructor(private val data: AppGuideJson) {
    fun app(key: String): AppInfo? = data.apps[key]
    fun forCountry(code: String?): CountryApps = code?.let { data.countries[it] } ?: data.default
    fun taxiFor(code: String?): AppInfo? = forCountry(code).taxi.firstNotNullOfOrNull { data.apps[it] }
    fun always(): List<AppInfo> = data.always.mapNotNull { data.apps[it] }
    fun isRussianSpace(code: String?): Boolean = code in setOf("RU", "BY", "KZ", "UZ", "KG", "AM")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun load(context: Context): AppGuide = parse(context.assets.open("apps.json").bufferedReader().use { it.readText() })
        fun parse(text: String): AppGuide = AppGuide(json.decodeFromString(text))
    }
}
