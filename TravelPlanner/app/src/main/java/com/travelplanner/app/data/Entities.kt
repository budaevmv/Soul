package com.travelplanner.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Переезд: перелёт или поезд (mode). Время хранится как момент (epoch millis) + часовой пояс
 * места отправления/прибытия, чтобы корректно показывать местное время и считать напоминания.
 * Для поездов коды аэропортов пустые, а станции лежат в fromName/toName.
 */
@Entity(
    tableName = "flights",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Flight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val airlineCode: String,
    val flightNumber: String,
    val fromIata: String,
    val toIata: String,
    val departureMillis: Long,
    val departureZone: String,
    val arrivalMillis: Long?,
    val arrivalZone: String,
    val departureTerminal: String = "",
    val bookingRef: String = "",
    val seat: String = "",
    val passenger: String = "",
    val remindMinutes: Int = 180,
    val calendarEventId: Long? = null,
    val attachmentPath: String? = null,
    val mode: String = MODE_FLIGHT,
    val fromName: String = "",
    val toName: String = "",
    val car: String = "",
) {
    val isTrain: Boolean get() = mode == MODE_TRAIN

    /** Ключ для поиска дубликатов: один и тот же рейс/поезд в одну дату. */
    fun dedupKey(): String = "$mode|$airlineCode|$flightNumber|${departureMillis / 3_600_000L}"

    companion object {
        const val MODE_FLIGHT = "FLIGHT"
        const val MODE_TRAIN = "TRAIN"
    }
}

/** Файл, загруженный в поездку (билет, бронь или нераспознанный документ). */
@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TripDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val path: String,
    /** FLIGHT, TRAIN, HOTEL, OTHER */
    val kind: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Проживание: отель, апартаменты и т.д. */
@Entity(
    tableName = "stays",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Stay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val address: String,
    val checkInMillis: Long,
    val checkOutMillis: Long,
    val zone: String,
    val bookingRef: String = "",
    val phone: String = "",
    val notes: String = "",
    val calendarEventId: Long? = null,
    val attachmentPath: String? = null,
)
