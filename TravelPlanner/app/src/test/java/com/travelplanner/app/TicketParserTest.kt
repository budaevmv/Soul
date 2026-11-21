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
        return TicketParser(ref.airports.keys, ref.airlines.keys, names, today) { t -> ref.citiesIn(t).map { it.city } }
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

    /** Квитанция агентства на рейс «Победы» (формат с таблицей; данные вымышленные). */
    @Test
    fun pobedaAgencyItinerary() {
        val text = Samples.POBEDA
        val doc = parser().parse(text)
        assertEquals(1, doc.flights.size)
        val f = doc.flights.single()
        assertEquals("DP", f.airlineCode)
        assertEquals("6864", f.number)
        assertEquals("GSV", f.from)
        assertEquals("SVO", f.to)
        assertEquals(LocalDate.of(2026, 11, 21), f.depDate)
        assertEquals(LocalTime.of(22, 30), f.depTime)
        assertEquals(LocalTime.of(23, 20), f.arrTime)
        assertEquals(LocalDate.of(2026, 11, 21), f.arrDate)
        assertEquals("QWRT2K", doc.bookingRef)
        assertEquals("IVANOV PETR", doc.passenger)
        // Саратов живёт по UTC+4
        assertEquals("Europe/Saratov", ref.zoneFor("GSV").id)
    }

    /**
     * Проверка на настоящем PDF: ./gradlew testDebugUnitTest -Pticket.pdf=/путь/к/билету.pdf
     * Без параметра тест пропускается (личные билеты в репозиторий не кладём).
     */
    @Test
    fun realPdfFromProperty() {
        val path = System.getProperty("ticket.pdf")
        org.junit.Assume.assumeTrue(!path.isNullOrBlank())
        val text = org.apache.pdfbox.pdmodel.PDDocument.load(File(path)).use { d ->
            org.apache.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(d)
        }
        val doc = parser().parse(text)
        println("TEXT:\n$text\nEND_TEXT")
        println("PARSED: $doc")
        assertTrue("ничего не найдено", !doc.isEmpty)
        doc.flights.forEach { f ->
            assertNotNull("from", f.from)
            assertNotNull("to", f.to)
            assertNotNull("date", f.depDate)
            assertNotNull("time", f.depTime)
        }
    }

    /** Квитанция агентства с блоками ВЫЛЕТ/ПРИЛЁТ и пересадкой (данные вымышленные). */
    @Test
    fun agencyReceiptWithDepartureArrivalBlocks() {
        val text = Samples.AGENCY
        val doc = parser().parse(text)
        assertEquals(2, doc.flights.size)
        val (a, b) = doc.flights
        assertEquals("FZ" to "1716", a.airlineCode to a.number)
        assertEquals("MSQ" to "DXB", a.from to a.to)
        assertEquals(LocalDate.of(2026, 11, 22), a.depDate)
        assertEquals(LocalTime.of(23, 50), a.depTime)
        assertEquals(LocalDate.of(2026, 11, 23), a.arrDate)
        assertEquals(LocalTime.of(8, 0), a.arrTime)
        assertEquals("EK" to "342", b.airlineCode to b.number)
        assertEquals("DXB" to "KUL", b.from to b.to)
        assertEquals(LocalDate.of(2026, 11, 23), b.depDate)
        assertEquals(LocalTime.of(10, 40), b.depTime)
        assertEquals(LocalTime.of(21, 55), b.arrTime)
        assertEquals("3", b.terminal)
        assertEquals("60A", b.seat)
        assertEquals("QWE7RT", doc.bookingRef)
        assertEquals("IVANOV PETR", doc.passenger)
    }

    /** Подтверждение брони отеля с датами «в колонках» (данные вымышленные). */
    @Test
    fun hotelVoucherWithColumnDates() {
        val text = Samples.HOTEL
        val doc = parser().parse(text)
        assertTrue(doc.flights.isEmpty())
        val stay = doc.stay!!
        assertEquals("Test Hills Hotel", stay.name)
        assertEquals("улица Тестовая, д.1, Москва", stay.address)
        assertEquals(LocalDate.of(2026, 11, 21), stay.checkIn)
        assertEquals(LocalTime.of(14, 0), stay.checkInTime)
        assertEquals(LocalDate.of(2026, 11, 22), stay.checkOut)
        assertEquals(LocalTime.of(12, 0), stay.checkOutTime)
        assertEquals("+79000000000", stay.phone)
        assertEquals("400000000001", doc.bookingRef)
    }

    /** Контрольный купон РЖД (данные вымышленные). */
    @Test
    fun rzdETicket() {
        val text = Samples.RZD
        val doc = parser().parse(text)
        assertTrue("не должно быть авиарейсов", doc.flights.isEmpty())
        assertNull(doc.stay)
        val t = doc.trains.single()
        assertEquals("717МА", t.number)
        assertEquals("МОСКВА БЕЛОРУССКАЯ", t.from)
        assertEquals("МИНСК", t.to)
        assertEquals(LocalDate.of(2026, 11, 22), t.depDate)
        assertEquals(LocalTime.of(6, 18), t.depTime)
        assertEquals(LocalDate.of(2026, 11, 22), t.arrDate)
        assertEquals(LocalTime.of(13, 24), t.arrTime)
        assertEquals("2", t.car)
        assertEquals("76", t.seat)
        assertEquals(false, t.moscowTime)
        assertEquals("70000000000001", doc.bookingRef)
        assertEquals("Europe/Minsk", ref.zoneForPlace(t.to!!)!!.id)
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
