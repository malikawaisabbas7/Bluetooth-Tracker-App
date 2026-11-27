package com.example.bluetoothtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DeviceEntity::class, SightingEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun sightingDao(): SightingDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sightings ADD COLUMN hasLocation INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE sightings SET hasLocation = 0 WHERE latitude = 0.0 AND longitude = 0.0")
            }
        }
    }
}
