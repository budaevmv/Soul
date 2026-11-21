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

    /** Город (по названию на русском или английском) -> аэропорт этого города (для часового пояса). */
    private val cityPatterns: List<Pair<Regex, Airport>> by lazy {
        val byCity = airports.values.groupBy { it.city.uppercase() }.mapValues { it.value.first() }
        val names = byCity.toMutableMap()
        EN_CITY_ALIASES.forEach { (en, iata) -> airports[iata]?.let { names[en] = it } }
        names.entries.sortedByDescending { it.key.length }.map { (name, a) ->
            Regex("(?<![А-ЯЁA-Z])" + Regex.escape(name) + "(?![А-ЯЁA-Z])") to a
        }
    }

    /** Все упоминания городов в тексте по порядку (без подряд идущих повторов). */
    fun citiesIn(text: String): List<Airport> {
        val upper = text.uppercase()
        val hits = mutableListOf<Pair<Int, Airport>>()
        val taken = mutableListOf<IntRange>()
        for ((re, a) in cityPatterns) {
            re.findAll(upper).forEach { m ->
                if (taken.none { it.first <= m.range.last && m.range.first <= it.last }) {
                    hits += m.range.first to a
                    taken += m.range
                }
            }
        }
        return hits.sortedBy { it.first }.map { it.second }
            .fold(mutableListOf()) { acc, a -> if (acc.lastOrNull()?.city != a.city) acc += a; acc }
    }

    /** Часовой пояс места по названию (вокзал «САРАТОВ 1 ПАСС», адрес отеля и т.п.). */
    fun zoneForPlace(text: String): ZoneId? = citiesIn(text).firstOrNull()?.zone

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

        private val EN_CITY_ALIASES = mapOf(
            "MOSCOW" to "SVO", "SAINT PETERSBURG" to "LED", "ST. PETERSBURG" to "LED", "SARATOV" to "GSV", "KAZAN" to "KZN",
            "SOCHI" to "AER", "MINSK" to "MSQ", "ISTANBUL" to "IST", "ANTALYA" to "AYT", "DUBAI" to "DXB", "ABU DHABI" to "AUH",
            "SHARJAH" to "SHJ", "DOHA" to "DOH", "KUALA LUMPUR" to "KUL", "BANGKOK" to "BKK", "SHAH ALAM" to "KUL", "SETIA ALAM" to "KUL", "PETALING JAYA" to "KUL", "PUTRAJAYA" to "KUL", "SEPANG" to "KUL", "PHUKET" to "HKT", "PATTAYA" to "UTP",
            "BALI" to "DPS", "DENPASAR" to "DPS", "SINGAPORE" to "SIN", "HO CHI MINH" to "SGN", "HANOI" to "HAN", "NHA TRANG" to "CXR",
            "TBILISI" to "TBS", "BATUMI" to "BUS", "YEREVAN" to "EVN", "BAKU" to "GYD", "ALMATY" to "ALA", "ASTANA" to "NQZ",
            "TASHKENT" to "TAS", "SAMARKAND" to "SKD", "BISHKEK" to "FRU", "CAIRO" to "CAI", "HURGHADA" to "HRG",
            "SHARM EL SHEIKH" to "SSH", "BEIJING" to "PEK", "SHANGHAI" to "PVG", "GUANGZHOU" to "CAN", "SANYA" to "SYX",
            "HONG KONG" to "HKG", "SEOUL" to "ICN", "TOKYO" to "HND", "DELHI" to "DEL", "NEW DELHI" to "DEL", "GOA" to "GOI",
            "COLOMBO" to "CMB", "BELGRADE" to "BEG", "TIVAT" to "TIV", "BUDVA" to "TIV", "LARNACA" to "LCA",
            "LIMASSOL" to "LCA", "PAPHOS" to "PFO", "ATHENS" to "ATH", "ROME" to "FCO", "MILAN" to "MXP", "PARIS" to "CDG",
            "BERLIN" to "BER", "BARCELONA" to "BCN", "MADRID" to "MAD", "PRAGUE" to "PRG", "VIENNA" to "VIE", "HAVANA" to "HAV",
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
