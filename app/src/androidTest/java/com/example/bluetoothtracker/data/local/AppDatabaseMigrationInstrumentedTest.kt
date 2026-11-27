package com.example.bluetoothtracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun validateCurrentVersion3Schema() {
        helper.createDatabase("migration-test-v3", 3).close()
    }

    @Test
    fun migrate2To3_preservesDevicesAndSetsHasLocationOnRealSqlite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "migration-test-v2-to-v3"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS devices (
                        mac TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        companyId INTEGER,
                        companyName TEXT,
                        category TEXT NOT NULL,
                        rssi INTEGER NOT NULL,
                        bondState INTEGER NOT NULL,
                        firstSeenAt INTEGER NOT NULL,
                        lastSeenAt INTEGER NOT NULL,
                        isIgnored INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sightings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mac TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        rssi INTEGER NOT NULL,
                        distanceM REAL NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sightings_mac ON sightings (mac)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )

        try {
            val db = openHelper.writableDatabase
            db.execSQL(
                "INSERT INTO devices VALUES ('AA:BB:CC:DD:EE:01', 'Tag 1', 76, 'Apple', 'AIR_TAG', -62, 10, 1000, 2000, 1)"
            )
            db.execSQL(
                "INSERT INTO sightings (mac, timestamp, rssi, distanceM, latitude, longitude) VALUES ('AA:BB:CC:DD:EE:01', 1500, -62, 1.8, 33.6844, 73.0479)"
            )
            db.execSQL(
                "INSERT INTO sightings (mac, timestamp, rssi, distanceM, latitude, longitude) VALUES ('AA:BB:CC:DD:EE:01', 1800, -70, 4.2, 0.0, 0.0)"
            )

            AppDatabase.MIGRATION_2_3.migrate(db)

            db.query("SELECT mac, firstSeenAt, lastSeenAt, isIgnored FROM devices").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("AA:BB:CC:DD:EE:01", c.getString(0))
                assertEquals(1000L, c.getLong(1))
                assertEquals(2000L, c.getLong(2))
                assertEquals(1, c.getInt(3))
            }

            db.query("SELECT timestamp, latitude, longitude, hasLocation FROM sightings ORDER BY timestamp ASC").use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(1500L, c.getLong(0))
                assertEquals(33.6844, c.getDouble(1), 0.0001)
                assertEquals(73.0479, c.getDouble(2), 0.0001)
                assertEquals(1, c.getInt(3))

                assertTrue(c.moveToNext())
                assertEquals(1800L, c.getLong(0))
                assertEquals(0.0, c.getDouble(1), 0.0001)
                assertEquals(0.0, c.getDouble(2), 0.0001)
                assertEquals(0, c.getInt(3))
            }
        } finally {
            openHelper.close()
            context.deleteDatabase(dbName)
        }
    }
}
