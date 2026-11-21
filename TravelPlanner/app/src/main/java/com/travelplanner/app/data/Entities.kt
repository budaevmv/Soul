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
 * Перелёт. Время хранится как момент (epoch millis) + часовой пояс аэропорта,
 * чтобы корректно показывать местное время вылета/прилёта и считать напоминания.
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
