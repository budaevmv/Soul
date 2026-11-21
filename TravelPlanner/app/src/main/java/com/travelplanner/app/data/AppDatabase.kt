package com.travelplanner.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Trip::class, Flight::class, Stay::class, TripDocument::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun flights(): FlightDao
    abstract fun stays(): StayDao
    abstract fun documents(): DocumentDao

    companion object {
        /** v1 -> v2: поезда (mode, станции, вагон) и таблица документов. Данные пользователя сохраняются. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flights ADD COLUMN mode TEXT NOT NULL DEFAULT 'FLIGHT'")
                db.execSQL("ALTER TABLE flights ADD COLUMN fromName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE flights ADD COLUMN toName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE flights ADD COLUMN car TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS documents (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, title TEXT NOT NULL, " +
                        "path TEXT NOT NULL, kind TEXT NOT NULL, addedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_tripId ON documents(tripId)")
                // Старые вложения перелётов/жилья тоже показываем как документы поездки
                db.execSQL(
                    "INSERT INTO documents (tripId, title, path, kind, addedAt) " +
                        "SELECT tripId, 'Билет ' || airlineCode || ' ' || flightNumber, attachmentPath, 'FLIGHT', 0 FROM flights " +
                        "WHERE attachmentPath IS NOT NULL GROUP BY attachmentPath",
                )
                db.execSQL(
                    "INSERT INTO documents (tripId, title, path, kind, addedAt) " +
                        "SELECT tripId, 'Бронь: ' || name, attachmentPath, 'HOTEL', 0 FROM stays " +
                        "WHERE attachmentPath IS NOT NULL AND attachmentPath NOT IN (SELECT path FROM documents) GROUP BY attachmentPath",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
