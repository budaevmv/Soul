package com.travelplanner.app.ui

import com.travelplanner.app.data.Flight
import com.travelplanner.app.reference.ReferenceData

/** Подписи для перелёта/поезда: города, станции, номер. */
fun Flight.fromCity(ref: ReferenceData): String =
    if (isTrain) ref.citiesIn(fromName).firstOrNull()?.city ?: fromName else ref.cityName(fromIata)

fun Flight.toCity(ref: ReferenceData): String =
    if (isTrain) ref.citiesIn(toName).firstOrNull()?.city ?: toName else ref.cityName(toIata)

/** «Москва (SVO)» или «Москва Белорусская». */
fun Flight.fromLabel(ref: ReferenceData): String = if (isTrain) fromName else "${ref.cityName(fromIata)} ($fromIata)"
fun Flight.toLabel(ref: ReferenceData): String = if (isTrain) toName else "${ref.cityName(toIata)} ($toIata)"

/** «SU 1234» или «Поезд 717МА». */
fun Flight.title(): String = if (isTrain) "Поезд $flightNumber" else "$airlineCode $flightNumber"

/** Запрос для карт: аэропорт или вокзал отправления. */
fun Flight.departurePlaceQuery(ref: ReferenceData): String =
    if (isTrain) "вокзал $fromName" else airportQuery(ref, fromIata)
