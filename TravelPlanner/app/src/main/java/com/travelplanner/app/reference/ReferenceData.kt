package com.travelplanner.app.reference

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId

data class Airport(
    val iata: String,
    val city: String,
    val name: String,
    val country: String,
    val zone: ZoneId,
) {
    val title: String get() = if (city == name) city else "$city, $name"
}

@Serializable
data class AirlineInfo(
    @SerialName("n") val name: String,
    @SerialName("c") val country: String = "",
    val web: String = "",
    /** За сколько часов открывается онлайн-регистрация (0 — нет/неизвестно). */
    @SerialName("oc") val onlineCheckinHours: Int = 24,
    /** За сколько минут до вылета закрывается регистрация в аэропорту. */
    @SerialName("cl") val airportCheckinCloseMinutes: Int = 40,
    val hand: String = "",
    val bag: String = "",
    val notes: List<String> = emptyList(),
)

@Serializable
data class CountryInfo(
    @SerialName("n") val name: String,
    @SerialName("f") val flag: String = "",
    @SerialName("cur") val currency: String = "",
    val plug: String = "",
    val sos: String = "",
    val visa: String = "",
    val money: String = "",
    @SerialName("no") val prohibited: List<String> = emptyList(),
    val take: List<String> = emptyList(),
    @SerialName("conn") val connectivity: String = "",
    val tips: List<String> = emptyList(),
    @SerialName("eu") val schengen: Boolean = false,
)

/**
 * Справочники: аэропорты, авиакомпании, страны. Хранятся в assets и работают без интернета.
 */
class ReferenceData(
    val airports: Map<String, Airport>,
    val airlines: Map<String, AirlineInfo>,
    val countries: Map<String, CountryInfo>,
) {
    fun airport(code: String?): Airport? = code?.let { airports[it.uppercase()] }
    fun airline(code: String?): AirlineInfo? = code?.let { airlines[it.uppercase()] }
    fun country(code: String?): CountryInfo? = code?.let { countries[it.uppercase()] }

    fun zoneFor(iata: String?): ZoneId = airport(iata)?.zone ?: ZoneId.systemDefault()

    fun cityName(iata: String): String = airport(iata)?.city ?: iata

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): ReferenceData {
            fun read(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
            return parse(read("airports.json"), read("airlines.json"), read("countries.json"))
        }

        fun parse(airportsJson: String, airlinesJson: String, countriesJson: String): ReferenceData {
            val rawAirports: Map<String, List<String>> = json.decodeFromString(airportsJson)
            val airports = rawAirports.mapValues { (code, v) ->
                Airport(code, v[0], v[1], v[2], runCatching { ZoneId.of(v[3]) }.getOrDefault(ZoneId.of("UTC")))
            }
            return ReferenceData(
                airports = airports,
                airlines = json.decodeFromString(airlinesJson),
                countries = json.decodeFromString(countriesJson),
            )
        }

        /** Общие правила авиационной безопасности (ИКАО/IATA), действуют почти у всех перевозчиков. */
        val generalCabinRules = listOf(
            "Жидкости в ручной клади — в ёмкостях до 100 мл, все вместе в прозрачном пакете объёмом до 1 л (в некоторых аэропортах с новыми сканерами ограничение снято — не рассчитывайте на это).",
            "Пауэрбанки и запасные литиевые батареи — ТОЛЬКО в ручной клади. До 100 Вт·ч — без ограничений по количеству в разумных пределах, 100–160 Вт·ч — не более 2 шт. и с разрешения авиакомпании, более 160 Вт·ч — запрещены. Всё чаще запрещено заряжаться от пауэрбанка на борту.",
            "Зажигалка — одна, при себе (не в багаже). Бензиновые зажигалки и спички — по правилам перевозчика.",
            "Ножи, ножницы с лезвием длиннее 6 см, инструменты, биты, спортивные палки — только в багаже.",
            "Лекарства в необходимом количестве можно в ручной клади, жидкие — сверх 100 мл при наличии рецепта/справки.",
            "Детское питание — в объёме, необходимом на время полёта.",
        )

        val generalBaggageRules = listOf(
            "Запрещено в багаже: пауэрбанки, запасные батареи, электронные сигареты, зажигалки.",
            "Запрещено везде: взрывчатые и легковоспламеняющиеся вещества, газовые баллоны, перцовые баллончики (у большинства перевозчиков), отравляющие вещества.",
            "Ноутбуки и электронику в багаже — выключить полностью, но лучше везти в салоне.",
            "Ценности, документы, лекарства, ключи — только в ручной клади.",
        )

        val schengenCommon = listOf(
            "Ввоз мяса, молочных продуктов и продуктов животного происхождения из стран вне ЕС запрещён.",
            "Наличные от 10 000 € — обязательно декларировать.",
            "Ввоз автомобилей с российскими номерами в большинство стран ЕС запрещён; также известны случаи изъятия личных вещей «санкционного» списка — уточняйте правила конкретной страны.",
        )

        val genericChecklist = listOf(
            "Загранпаспорт (срок действия — минимум 6 месяцев после возвращения).",
            "Медицинская страховка с покрытием в стране поездки.",
            "Распечатки или PDF: билеты, брони отелей, страховка.",
            "Наличные в валюте (USD/EUR) — российские карты за рубежом в большинстве стран не работают.",
            "Переходник для розеток, зарядки, пауэрбанк.",
            "Лекарства + рецепты к ним.",
            "Офлайн-карты и переводчик.",
        )
    }
}
