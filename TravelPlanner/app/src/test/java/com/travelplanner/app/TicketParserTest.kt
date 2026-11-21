package com.travelplanner.app

import com.travelplanner.app.importer.BcbpParser
import com.travelplanner.app.importer.TicketParser
import com.travelplanner.app.reference.ReferenceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class TicketParserTest {

    private val ref: ReferenceData = run {
        val dir = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.exists() }
        ReferenceData.parse(File(dir, "airports.json").readText(), File(dir, "airlines.json").readText(), File(dir, "countries.json").readText())
    }
    private val today = LocalDate.of(2026, 11, 21)

    private fun parser(): TicketParser {
        val names = mutableMapOf<String, String>()
        ref.airports.values.forEach { if (it.name.length >= 5 && it.name != it.city) names[it.name.uppercase()] = it.iata }
        ref.airports.values.groupBy { it.city }.forEach { (city, l) -> if (l.size == 1 && city.length >= 4) names[city.uppercase()] = l.first().iata }
        return TicketParser(ref.airports.keys, ref.airlines.keys, names, today)
    }

    @Test
    fun englishItineraryWithTwoSegments() {
        val text = """
            ELECTRONIC TICKET ITINERARY RECEIPT
            Passenger: IVANOV/IVAN MR
            Booking reference: XK7Q2L
            Ticket number: 235 2401234567
            FLIGHT  FROM               TO                 DATE       DEP    ARR
            TK 414  Moscow (VKO)       Istanbul (IST)     12DEC26    10:05  13:30   Terminal A
            Seat 14C
            TK 1981 Istanbul (IST)     Antalya (AYT)      12 Dec 2026 16:40 18:10
        """.trimIndent()
        val doc = parser().parse(text)
        assertEquals("XK7Q2L", doc.bookingRef)
        assertEquals(2, doc.flights.size)
        val f1 = doc.flights[0]
        assertEquals("TK", f1.airlineCode)
        assertEquals("414", f1.number)
        assertEquals("VKO", f1.from)
        assertEquals("IST", f1.to)
        assertEquals(LocalDate.of(2026, 12, 12), f1.depDate)
        assertEquals(LocalTime.of(10, 5), f1.depTime)
        assertEquals(LocalTime.of(13, 30), f1.arrTime)
        assertEquals("A", f1.terminal)
        assertEquals("14C", f1.seat)
        val f2 = doc.flights[1]
        assertEquals("1981", f2.number)
        assertEquals("IST", f2.from)
        assertEquals("AYT", f2.to)
        assertEquals(LocalTime.of(16, 40), f2.depTime)
        assertNull(doc.stay)
    }

    @Test
    fun russianItineraryWithCyrillicCarrierAndAirportNames() {
        val text = """
            Маршрутная квитанция электронного билета
            Пассажир: ПЕТРОВА АННА
            Код бронирования: ЖЩ4К2Р
            Рейс СУ 1124  Москва, Шереметьево, терминал B — Сочи
            Вылет 21.12.2026 07:35  Прилёт 21.12.2026 10:05
        """.trimIndent()
        val doc = parser().parse(text)
        assertEquals(1, doc.flights.size)
        val f = doc.flights[0]
        assertEquals("SU", f.airlineCode)
        assertEquals("1124", f.number)
        assertEquals("SVO", f.from)
        assertEquals("AER", f.to)
        assertEquals(LocalDate.of(2026, 12, 21), f.depDate)
        assertEquals(LocalTime.of(7, 35), f.depTime)
        assertEquals(LocalTime.of(10, 5), f.arrTime)
        assertEquals("B", f.terminal)
        assertEquals("ЖЩ4К2Р", doc.bookingRef)
    }

    @Test
    fun overnightArrivalMovesToNextDay() {
        val text = "Flight EK 132 DME - DXB 30/12/2026 23:50 06:05"
        val f = parser().parse(text).flights.single()
        assertEquals(LocalDate.of(2026, 12, 30), f.depDate)
        assertEquals(LocalDate.of(2026, 12, 31), f.arrDate)
    }

    @Test
    fun hotelBookingInRussian() {
        val text = """
            Подтверждение бронирования
            Отель: Grand Bazaar Suites
            Адрес: Mimar Hayrettin Mah. 12, Fatih, Istanbul
            Заезд: 12 декабря 2026, с 14:00
            Выезд: 16 декабря 2026, до 12:00
            Телефон: +90 212 555 12 34
            Номер бронирования: 4821990
        """.trimIndent()
        val doc = parser().parse(text)
        val stay = assertNotNull(doc.stay).let { doc.stay!! }
        assertEquals("Grand Bazaar Suites", stay.name)
        assertEquals("Mimar Hayrettin Mah. 12, Fatih, Istanbul", stay.address)
        assertEquals(LocalDate.of(2026, 12, 12), stay.checkIn)
        assertEquals(LocalTime.of(14, 0), stay.checkInTime)
        assertEquals(LocalDate.of(2026, 12, 16), stay.checkOut)
        assertEquals("+90 212 555 12 34", stay.phone)
        assertTrue(doc.flights.isEmpty())
    }

    @Test
    fun boardingPassBarcode() {
        val data = "M1IVANOV/IVAN         EXK7Q2L VKOISTTK 0414 346Y014C0045 100"
        val r = BcbpParser.parse(data, today)!!
        val f = r.flights.single()
        assertEquals("TK", f.airlineCode)
        assertEquals("414", f.number)
        assertEquals("VKO", f.from)
        assertEquals("IST", f.to)
        assertEquals(LocalDate.of(2026, 12, 12), f.depDate)
        assertEquals("14C", f.seat)
        assertEquals("XK7Q2L", r.bookingRef)
    }

    @Test
    fun noFalsePositivesInPlainText() {
        val doc = parser().parse("Hello, we can meet at 10:00 on Monday. Price 2500 RUB, code AB 12.")
        assertTrue(doc.flights.isEmpty())
    }

    @Test
    fun referenceDataIsConsistent() {
        ref.airports.values.forEach { assertTrue("unknown country ${it.country} for ${it.iata}", it.country.length == 2) }
        assertNotNull(ref.airline("SU"))
        assertNotNull(ref.country("TR"))
    }
}
