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

    /** Квитанция агентства на рейс «Победы» (формат с таблицей; данные вымышленные). */
    @Test
    fun pobedaAgencyItinerary() {
        val text = """
                                                  Electronic ticket
                                                  Дата оформления заказа: 20.11.2026
             Пассажир                Паспорт РФ          Номер заказа          Номер электронного билета
             Passenger               Passport            Order number          E-ticket number
             IVANOV PETR
             SERGEEVICH              1234567890          40000001              4250000000001
             Saratov → Moscow        Отправление         Местное время                       Прибытие
             1 h 50 min              Departure           Local time                          Arrival
             DP-6864 Победа          Saratov             22:30           23:20               Moscow
             Номер для онлайн-регистрации / Check-in:
                                     Gagarin GSV         21 NOV 2026     21 NOV 2026         Sheremetyevo SVO
             QWRT2K
             Багаж / Baggage allowance: 10 kg
            Маршрутная квитанция электронного билета                                          Победа
            Клиент
                                                                                    Код бронирования
            ФИО                    Дата рождения    Документ      Номер билета      Категория
            IVANOV PETR            01.01.1990       1234567890    4250000000001     Взрослый      QWRT2K
              Саратов           Москва
              Номер рейса       Вылет                     В пути         Прилёт
              DP6864            21 ноября 2026 22:30      01 ч 50 мин    21 ноября 2026 23:20
                                Саратов                                  Москва
                                GSV | Гагарин                            SVO | Шереметьево | D
        """.trimIndent()
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
        val text = """
                           ООО "Тест Тревел", Тел: +7 (495) 000-00-00                       БРОНИРОВАНИЕ
                           IATA 90000000                                                    ПОДТВЕРЖДЕНО
                           КВИТАНЦИЯ ПО ЗАКАЗУ /
                           ЭЛ. БИЛЕТ
                           ФАМИЛИЯ И ИМЯ ПАССАЖИРА /                      ДАТА / ЗАКАЗ № / ТЕЛЕФОН ПОКУПАТЕЛЯ
                           ДОКУМЕНТ                               20 НОЯБРЯ 2026 Г., 17:30 / F20000000000 /
                           IVANOV PETR /                                        +7900***00
                           700000000
             ВЫЛЕТ:        22 НОЯБ. 2026 Г.                 АЭРОПОРТ МИНСК
                           23:50                            МИНСК, БЕЛАРУСЬ                     MSQ
            Рейс#          FZ-1716                          Эконом (KLSOSRU1)
       Эл. билет#          1760000000000                    Осуществляет авиакомпания FlyDubai
          Бронь#           QWE7RT;ASD8FG, "FZ"              Ручная кладь: 1 багажное место 7кг на пассажира 55x38x20
           Статус          HK                               Багаж: 1 багажное место 30кг на пассажира
           ПРИЛЁТ:         23 НОЯБ. 2026 Г.                 АЭРОПОРТ ДУБАЙ , ТЕРМИНАЛ 3
                           8:00                             ДУБАЙ, ОАЭ                           DXB
             ВЫЛЕТ:        23 НОЯБ. 2026 Г.                 АЭРОПОРТ ДУБАЙ , ТЕРМИНАЛ 3
                           10:40                            ДУБАЙ, ОАЭ                           DXB
            Рейс#          EK-342                           Эконом (KLSOSRU1)
          Бронь#           QWE7RT;ASD8FG, "EK"              Ручная кладь: 1 багажное место 7кг на пассажира 55x38x22
           Статус          HK                               Место на борту: 60A
           ПРИЛЁТ:         23 НОЯБ. 2026 Г.                 АЭРОПОРТ КУАЛА-ЛУМПУР , ТЕРМИНАЛ 1
                           21:55                            КУАЛА-ЛУМПУР, МАЛАЙЗИЯ               KUL
            ТАКСА B8                                               413.00   RUB
            ТАКСА ZR                                               113.00   RUB
            РАСЧЕТ ТАРИФА MSQ FZ X/DXB EK KUL552.06NUC552.06END ROE0.891192
            DEPARTURE:               NOV 22, 2026                           MINSK AIRPORT
                                     23:50                                  MINSK, BY            MSQ
                   Flight#           FZ-1716                                Economy (KLSOSRU1)
                 ARRIVAL:            NOV 23, 2026                           DUBAI AIRPORT , TERMINAL 3
                                     8:00                                   DUBAI, AE            DXB
            DEPARTURE:               NOV 23, 2026                           DUBAI AIRPORT , TERMINAL 3
                                     10:40                                  DUBAI, AE            DXB
                   Flight#           EK-342                                 Economy (KLSOSRU1)
                 ARRIVAL:            NOV 23, 2026                           KUALA LUMPUR AIRPORT , TERMINAL 1
                                     21:55                                  KUALA LUMPUR, MY     KUL
        """.trimIndent()
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
        val text = """
                                                                  Команда поддержки 24/7
                                     8 800 000-00-00 по России, +7 499 000-00-00 из-за рубежа
                                                   Заезд                  Отъезд            1 номер
            Бронирование от 20.11.2026
            № 400000000001
            № партнера 1000000
                                                   21           –          22                  1
                                                  ноября                 ноября               ночь
                                                  с 14:00                до 12:00

            Test Hills Hotel

                            Адрес отеля
                            улица Тестовая, д.1, Москва
                            Телефон                 Почта
                Отель на
                 карте
                            +79000000000            hotel@example.com
            Вы забронировали Стандарт одноместный
        """.trimIndent()
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
