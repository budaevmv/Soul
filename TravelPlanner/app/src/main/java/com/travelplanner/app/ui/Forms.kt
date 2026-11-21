package com.travelplanner.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.importer.ParsedFlight
import com.travelplanner.app.importer.ParsedStay
import com.travelplanner.app.reference.ReferenceData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Редактируемое состояние перелёта (используется и в редакторе, и в предпросмотре импорта). */
class FlightForm(
    airline: String = "",
    number: String = "",
    from: String = "",
    to: String = "",
    depDate: LocalDate? = null,
    depTime: LocalTime? = null,
    arrDate: LocalDate? = null,
    arrTime: LocalTime? = null,
    terminal: String = "",
    seat: String = "",
    booking: String = "",
    passenger: String = "",
    remindHours: Int = 3,
    val base: Flight? = null,
    mode: String = Flight.MODE_FLIGHT,
    fromName: String = "",
    toName: String = "",
    car: String = "",
    remindMinutes: Int = remindHours * 60,
) {
    var mode by mutableStateOf(mode)
    var fromName by mutableStateOf(fromName)
    var toName by mutableStateOf(toName)
    var car by mutableStateOf(car)
    var remindMinutes by mutableStateOf(remindMinutes)
    val isTrain: Boolean get() = mode == Flight.MODE_TRAIN

    /** Файл, из которого распознан черновик (для пунктов, дополняемых вручную). */
    var draftAttachment: String? = null
    var airline by mutableStateOf(airline)
    var number by mutableStateOf(number)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)
    var depDate by mutableStateOf(depDate)
    var depTime by mutableStateOf(depTime)
    var arrDate by mutableStateOf(arrDate)
    var arrTime by mutableStateOf(arrTime)
    var terminal by mutableStateOf(terminal)
    var seat by mutableStateOf(seat)
    var booking by mutableStateOf(booking)
    var passenger by mutableStateOf(passenger)
    var selected by mutableStateOf(true)

    /** Чего не хватает для сохранения. */
    fun missing(): List<String> = if (isTrain) buildList {
        if (number.isBlank()) add("номер поезда")
        if (fromName.isBlank()) add("станция отправления")
        if (toName.isBlank()) add("станция прибытия")
        if (depDate == null) add("дата отправления")
        if (depTime == null) add("время отправления")
    } else buildList {
        if (airline.isBlank() || number.isBlank()) add("номер рейса")
        if (from.length != 3) add("аэропорт вылета")
        if (to.length != 3) add("аэропорт прилёта")
        if (depDate == null) add("дата вылета")
        if (depTime == null) add("время вылета")
    }

    fun toFlight(tripId: Long, ref: ReferenceData, attachment: String? = base?.attachmentPath): Flight? {
        if (missing().isNotEmpty()) return null
        if (isTrain) return toTrain(tripId, ref, attachment)
        val depZone = ref.zoneFor(from).id
        val arrZone = ref.zoneFor(to).id
        val dep = Format.toMillis(depDate!!, depTime!!, depZone)
        val arr = arrTime?.let { t -> Format.toMillis(arrDate ?: depDate!!, t, arrZone) }?.let { a ->
            // Прилёт «раньше» вылета по местному времени при отсутствии даты прилёта => следующий день
            if (a <= dep && arrDate == null) a + 24 * 3_600_000L else a
        }
        val b = base ?: Flight(
            tripId = tripId, airlineCode = "", flightNumber = "", fromIata = "", toIata = "",
            departureMillis = 0, departureZone = depZone, arrivalMillis = null, arrivalZone = arrZone,
        )
        return b.copy(
            tripId = tripId,
            airlineCode = airline.trim().uppercase(),
            flightNumber = number.trim().uppercase(),
            fromIata = from.trim().uppercase(),
            toIata = to.trim().uppercase(),
            departureMillis = dep,
            departureZone = depZone,
            arrivalMillis = arr,
            arrivalZone = arrZone,
            departureTerminal = terminal.trim(),
            seat = seat.trim().uppercase(),
            bookingRef = booking.trim().uppercase(),
            passenger = passenger.trim(),
            remindMinutes = remindMinutes,
            attachmentPath = attachment,
            mode = Flight.MODE_FLIGHT,
        )
    }

    private fun toTrain(tripId: Long, ref: ReferenceData, attachment: String?): Flight {
        val depZone = (ref.zoneForPlace(fromName) ?: java.time.ZoneId.systemDefault()).id
        val arrZone = (ref.zoneForPlace(toName)?.id) ?: depZone
        val dep = Format.toMillis(depDate!!, depTime!!, depZone)
        val arr = arrTime?.let { t -> Format.toMillis(arrDate ?: depDate!!, t, arrZone) }?.let { a ->
            if (a <= dep && arrDate == null) a + 24 * 3_600_000L else a
        }
        val b = base ?: Flight(
            tripId = tripId, airlineCode = "", flightNumber = "", fromIata = "", toIata = "",
            departureMillis = 0, departureZone = depZone, arrivalMillis = null, arrivalZone = arrZone,
        )
        return b.copy(
            tripId = tripId, airlineCode = "", flightNumber = number.trim().uppercase(), fromIata = "", toIata = "",
            departureMillis = dep, departureZone = depZone, arrivalMillis = arr, arrivalZone = arrZone,
            departureTerminal = "", seat = seat.trim(), car = car.trim(), bookingRef = booking.trim(), passenger = passenger.trim(),
            remindMinutes = remindMinutes, attachmentPath = attachment, mode = Flight.MODE_TRAIN,
            fromName = fromName.trim(), toName = toName.trim(),
        )
    }

    companion object {
        fun of(f: Flight): FlightForm {
            val dep = Format.local(f.departureMillis, f.departureZone)
            val arr = f.arrivalMillis?.let { Format.local(it, f.arrivalZone) }
            return FlightForm(
                f.airlineCode, f.flightNumber, f.fromIata, f.toIata,
                dep.toLocalDate(), dep.toLocalTime(), arr?.toLocalDate(), arr?.toLocalTime(),
                f.departureTerminal, f.seat, f.bookingRef, f.passenger, base = f,
                mode = f.mode, fromName = f.fromName, toName = f.toName, car = f.car, remindMinutes = f.remindMinutes,
            )
        }

        fun of(p: ParsedFlight, booking: String?, passenger: String?, remindHours: Int) = FlightForm(
            p.airlineCode, p.number, p.from ?: "", p.to ?: "", p.depDate, p.depTime, p.arrDate, p.arrTime,
            p.terminal ?: "", p.seat ?: "", booking ?: "", passenger ?: "", remindHours,
        )

        fun of(p: com.travelplanner.app.importer.ParsedTrain, booking: String?, passenger: String?, remindMinutes: Int) = FlightForm(
            number = p.number, depDate = p.depDate, depTime = p.depTime, arrDate = p.arrDate, arrTime = p.arrTime,
            seat = p.seat ?: "", booking = booking ?: "", passenger = passenger ?: "",
            mode = Flight.MODE_TRAIN,
            fromName = p.from?.let { com.travelplanner.app.importer.Converters.titleCase(it) } ?: "",
            toName = p.to?.let { com.travelplanner.app.importer.Converters.titleCase(it) } ?: "",
            car = p.car ?: "", remindMinutes = remindMinutes,
        )
    }
}

class StayForm(
    name: String = "",
    address: String = "",
    checkInDate: LocalDate? = null,
    checkInTime: LocalTime? = LocalTime.of(14, 0),
    checkOutDate: LocalDate? = null,
    checkOutTime: LocalTime? = LocalTime.of(12, 0),
    booking: String = "",
    phone: String = "",
    notes: String = "",
    val base: Stay? = null,
) {
    var name by mutableStateOf(name)
    var address by mutableStateOf(address)
    var checkInDate by mutableStateOf(checkInDate)
    var checkInTime by mutableStateOf(checkInTime)
    var checkOutDate by mutableStateOf(checkOutDate)
    var checkOutTime by mutableStateOf(checkOutTime)
    var booking by mutableStateOf(booking)
    var phone by mutableStateOf(phone)
    var notes by mutableStateOf(notes)
    var selected by mutableStateOf(true)
    var draftAttachment: String? = null

    fun missing(): List<String> = buildList {
        if (name.isBlank()) add("название")
        if (checkInDate == null) add("дата заезда")
        if (checkOutDate == null) add("дата выезда")
    }

    /** Часовой пояс жилья — по аэропорту прилёта ближайшего предшествующего рейса поездки. */
    fun toStay(tripId: Long, flights: List<Flight>, attachment: String? = base?.attachmentPath): Stay? {
        if (missing().isNotEmpty()) return null
        val zone = zoneFor(checkInDate!!, flights)
        val inMs = Format.toMillis(checkInDate!!, checkInTime ?: LocalTime.of(14, 0), zone)
        val outMs = Format.toMillis(checkOutDate!!, checkOutTime ?: LocalTime.of(12, 0), zone)
        val b = base ?: Stay(tripId = tripId, name = "", address = "", checkInMillis = 0, checkOutMillis = 0, zone = zone)
        return b.copy(
            tripId = tripId, name = name.trim(), address = address.trim(),
            checkInMillis = inMs, checkOutMillis = maxOf(outMs, inMs), zone = zone,
            bookingRef = booking.trim(), phone = phone.trim(), notes = notes.trim(), attachmentPath = attachment,
        )
    }

    companion object {
        fun zoneFor(checkIn: LocalDate, flights: List<Flight>): String {
            val endOfDay = checkIn.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            return flights.filter { (it.arrivalMillis ?: it.departureMillis) <= endOfDay }
                .maxByOrNull { it.departureMillis }?.arrivalZone
                ?: flights.firstOrNull()?.arrivalZone
                ?: java.time.ZoneId.systemDefault().id
        }

        fun of(s: Stay): StayForm {
            val a = Format.local(s.checkInMillis, s.zone)
            val b = Format.local(s.checkOutMillis, s.zone)
            return StayForm(
                s.name, s.address, a.toLocalDate(), a.toLocalTime(), b.toLocalDate(), b.toLocalTime(),
                s.bookingRef, s.phone, s.notes, base = s,
            )
        }

        fun of(p: ParsedStay, booking: String?) = StayForm(
            p.name ?: "", p.address ?: "", p.checkIn, p.checkInTime, p.checkOut, p.checkOutTime, booking ?: "", p.phone ?: "",
        )
    }
}

// ---------- Поля ввода ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value?.let { Format.date(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = value == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: LocalTime?, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier, required: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value?.let { Format.time(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = required && value == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberTimePickerState(value?.hour ?: 12, value?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена") } },
            text = { TimePicker(state = state) },
        )
    }
}

/** Поле кода аэропорта IATA с подсказками по названию города. */
@Composable
fun AirportField(label: String, value: String, onChange: (String) -> Unit, ref: ReferenceData, modifier: Modifier = Modifier) {
    var query by remember(value) { mutableStateOf(value) }
    val airport = ref.airport(value)
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { v ->
                query = v
                val code = v.trim().uppercase()
                if (code.length == 3 && code.all { it in 'A'..'Z' }) onChange(code) else if (v.isBlank()) onChange("")
            },
            label = { Text(label) },
            singleLine = true,
            isError = value.length != 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = {
                Text(
                    when {
                        airport != null -> "${airport.title} · ${Format.utcOffset(airport.zone.id)}"
                        value.length == 3 -> "Код не найден в справочнике — время будет в поясе телефона"
                        else -> "Код IATA (SVO) или начните вводить город"
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val q = query.trim()
        if (q.length >= 2 && ref.airport(q) == null) {
            val suggestions = remember(q) {
                ref.airports.values.filter {
                    it.city.startsWith(q, ignoreCase = true) || it.name.startsWith(q, ignoreCase = true) || it.iata.startsWith(q, ignoreCase = true)
                }.take(8)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions) { a ->
                    SuggestionChip(onClick = {
                        query = a.iata
                        onChange(a.iata)
                    }, label = { Text("${a.iata} · ${a.city}") })
                }
            }
        }
    }
}

@Composable
fun FlightFormFields(form: FlightForm, ref: ReferenceData, compact: Boolean = false) {
    if (form.isTrain) {
        TrainFormFields(form, ref)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.airline, onValueChange = { form.airline = it.uppercase().take(3) },
                label = { Text("Код а/к") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = { ref.airline(form.airline)?.let { Text(it.name) } },
            )
            OutlinedTextField(
                value = form.number, onValueChange = { form.number = it.filter { c -> c.isLetterOrDigit() }.take(5) },
                label = { Text("Номер рейса") }, singleLine = true, modifier = Modifier.weight(1.4f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        AirportField("Откуда", form.from, { form.from = it }, ref)
        AirportField("Куда", form.to, { form.to = it }, ref)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Дата вылета", form.depDate, { form.depDate = it }, Modifier.weight(1.3f))
            TimeField("Вылет", form.depTime, { form.depTime = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Дата прилёта", form.arrDate ?: form.depDate, { form.arrDate = it }, Modifier.weight(1.3f))
            TimeField("Прилёт", form.arrTime, { form.arrTime = it }, Modifier.weight(1f), required = false)
        }
        Text("Время — местное для аэропорта", style = MaterialTheme.typography.bodySmall)
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.terminal, { form.terminal = it.take(3) }, label = { Text("Терминал") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(form.seat, { form.seat = it.take(4) }, label = { Text("Место") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(form.booking, { form.booking = it }, label = { Text("Код бронирования") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.passenger, { form.passenger = it }, label = { Text("Пассажир") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun TrainFormFields(form: FlightForm, ref: ReferenceData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            form.number, { form.number = it.take(6) }, label = { Text("Номер поезда (например 717МА)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), isError = form.number.isBlank(),
        )
        OutlinedTextField(
            form.fromName, { form.fromName = it }, label = { Text("Станция отправления") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), isError = form.fromName.isBlank(),
            supportingText = { ref.citiesIn(form.fromName).firstOrNull()?.let { Text("${it.city} · ${Format.utcOffset(it.zone.id)}") } },
        )
        OutlinedTextField(
            form.toName, { form.toName = it }, label = { Text("Станция прибытия") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), isError = form.toName.isBlank(),
            supportingText = { ref.citiesIn(form.toName).firstOrNull()?.let { Text("${it.city} · ${Format.utcOffset(it.zone.id)}") } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Дата отправления", form.depDate, { form.depDate = it }, Modifier.weight(1.3f))
            TimeField("Отправление", form.depTime, { form.depTime = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Дата прибытия", form.arrDate ?: form.depDate, { form.arrDate = it }, Modifier.weight(1.3f))
            TimeField("Прибытие", form.arrTime, { form.arrTime = it }, Modifier.weight(1f), required = false)
        }
        Text("Время — местное для станции", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(form.car, { form.car = it.take(3) }, label = { Text("Вагон") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(form.seat, { form.seat = it.take(4) }, label = { Text("Место") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(form.booking, { form.booking = it }, label = { Text("Номер заказа") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun StayFormFields(form: StayForm) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(form.name, { form.name = it }, label = { Text("Отель / апартаменты") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = form.name.isBlank())
        OutlinedTextField(form.address, { form.address = it }, label = { Text("Адрес") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Заезд", form.checkInDate, { form.checkInDate = it }, Modifier.weight(1.3f))
            TimeField("С", form.checkInTime, { form.checkInTime = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Выезд", form.checkOutDate, { form.checkOutDate = it }, Modifier.weight(1.3f))
            TimeField("До", form.checkOutTime, { form.checkOutTime = it }, Modifier.weight(1f))
        }
        OutlinedTextField(form.booking, { form.booking = it }, label = { Text("Номер брони") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            form.phone, { form.phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        OutlinedTextField(form.notes, { form.notes = it }, label = { Text("Заметки (код от двери, как добраться)") }, modifier = Modifier.fillMaxWidth())
    }
}
