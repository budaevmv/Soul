package com.travelplanner.app.data

import com.travelplanner.app.reference.CountryInfo
import kotlin.math.ceil

/** Список вещей с учётом погоды, страны и длительности поездки. */
object PackingList {
    data class Section(val title: String, val emoji: String, val items: List<String>)

    private val TROPICS = setOf("TH", "VN", "MY", "ID", "SG", "PH", "LK", "IN", "MV", "EG", "AE", "QA", "CU", "DO", "TZ", "MU", "SC")

    fun build(
        weather: List<StopWeather>,
        countries: List<Pair<String, CountryInfo?>>,
        tripDays: Int,
        abroad: Boolean,
        hasFlight: Boolean,
        noGoogleServices: Boolean,
    ): List<Section> {
        val days = tripDays.coerceIn(1, 30)
        val minT = weather.minOfOrNull { it.minT }
        val maxT = weather.maxOfOrNull { it.maxT }
        val rain = weather.sumOf { it.rainyDays }
        val tropical = countries.any { it.first in TROPICS } || (maxT ?: 0.0) >= 28

        val docs = buildList {
            add(if (abroad) "Загранпаспорт (действует ещё 6 месяцев после возвращения)" else "Паспорт")
            if (abroad) add("Медицинская страховка (PDF в телефоне + распечатка)")
            add("Билеты и брони — уже в приложении, доступны без интернета")
            add("Банковская карта — пластиком, не только в телефоне")
            if (abroad) add("Наличные USD/EUR: российские карты за рубежом почти везде не работают")
            if (abroad && noGoogleServices) add("Второй телефон с сервисами Google или пластиковая карта: на этом телефоне многие приложения оплаты и такси не работают")
            if (abroad) add("Российская SIM-карта активна (SMS-коды банков)")
        }
        val clothes = buildList {
            add("Нижнее бельё и носки — ${minOf(days + 1, 10)} комплектов")
            add("Футболки — ${minOf(ceil(days * 0.8).toInt().coerceAtLeast(2), 8)} шт.")
            add("Удобная обувь для прогулок")
            if (maxT != null && maxT >= 24) {
                add("Шорты и лёгкая одежда")
                add("Сандалии или шлёпанцы")
                add("Головной убор и солнцезащитные очки")
            }
            if (maxT != null && maxT >= 27 || tropical) add("Купальник / плавки")
            if (minT == null || minT < 16) add("Кофта или толстовка")
            if (minT != null && minT < 8) add("Тёплая куртка")
            if (minT != null && minT <= 0) {
                add("Шапка, шарф и перчатки")
                add("Тёплая непромокаемая обувь, термобельё")
            }
            if (rain > 0) add("Зонт или дождевик (ожидаются дожди: $rain дн.)")
            if (hasFlight) add("Кофта в самолёт — в салоне прохладно")
            add("Одежда для сна")
        }
        val care = buildList {
            add("Зубная щётка и паста, дезодорант (жидкости в ручной клади — до 100 мл)")
            add("Личные лекарства + рецепты к ним")
            add("Аптечка: обезболивающее, от расстройства желудка, пластыри, антисептик")
            if ((maxT ?: 0.0) >= 22 || tropical) add("Солнцезащитный крем SPF 30–50")
            if (tropical) add("Средство от комаров")
        }
        val tech = buildList {
            add("Телефон и зарядка")
            add(if (hasFlight) "Пауэрбанк — только в ручную кладь, до 100 Вт·ч" else "Пауэрбанк")
            countries.mapNotNull { it.second }.filter { c -> !c.plug.contains("C") && !c.plug.contains("F") }.forEach { c ->
                add("Переходник для розеток ${c.plug.substringBefore("·").trim()} (${c.name})")
            }
            add("Наушники")
            if (abroad) add("eSIM или местная SIM для интернета")
        }
        val local = countries.flatMap { (_, c) -> c?.take.orEmpty() }.distinct()
            .filterNot { it.contains("паспорт", true) || it.contains("страховк", true) }

        return listOfNotNull(
            Section("Документы и деньги", "🛂", docs),
            Section("Одежда", "👕", clothes),
            Section("Гигиена и аптечка", "🧴", care),
            Section("Техника", "🔌", tech),
            local.takeIf { it.isNotEmpty() }?.let { Section("Особенности страны", "🌍", it) },
        )
    }
}
