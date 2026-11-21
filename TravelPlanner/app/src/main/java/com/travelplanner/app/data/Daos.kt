package com.travelplanner.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): Trip?

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)
}

@Dao
interface FlightDao {
    @Query("SELECT * FROM flights ORDER BY departureMillis")
    fun observeAll(): Flow<List<Flight>>

    @Query("SELECT * FROM flights WHERE tripId = :tripId ORDER BY departureMillis")
    fun observeForTrip(tripId: Long): Flow<List<Flight>>

    @Query("SELECT * FROM flights WHERE tripId = :tripId ORDER BY departureMillis")
    suspend fun forTrip(tripId: Long): List<Flight>

    @Query("SELECT * FROM flights WHERE departureMillis > :fromMillis")
    suspend fun upcoming(fromMillis: Long): List<Flight>

    @Query("SELECT * FROM flights WHERE id = :id")
    suspend fun get(id: Long): Flight?

    @Insert
    suspend fun insert(flight: Flight): Long

    @Update
    suspend fun update(flight: Flight)

    @Delete
    suspend fun delete(flight: Flight)
}

@Dao
interface StayDao {
    @Query("SELECT * FROM stays ORDER BY checkInMillis")
    fun observeAll(): Flow<List<Stay>>

    @Query("SELECT * FROM stays WHERE tripId = :tripId ORDER BY checkInMillis")
    fun observeForTrip(tripId: Long): Flow<List<Stay>>

    @Query("SELECT * FROM stays WHERE tripId = :tripId ORDER BY checkInMillis")
    suspend fun forTrip(tripId: Long): List<Stay>

    @Query("SELECT * FROM stays WHERE checkOutMillis > :fromMillis")
    suspend fun upcoming(fromMillis: Long): List<Stay>

    @Query("SELECT * FROM stays WHERE id = :id")
    suspend fun get(id: Long): Stay?

    @Insert
    suspend fun insert(stay: Stay): Long

    @Update
    suspend fun update(stay: Stay)

    @Delete
    suspend fun delete(stay: Stay)
}
