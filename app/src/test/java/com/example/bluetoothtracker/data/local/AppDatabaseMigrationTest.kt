package com.example.bluetoothtracker.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class AppDatabaseMigrationTest {

    @Test
    fun `migration 2 to 3 adds hasLocation column without dropping data`() {
        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)

        AppDatabase.MIGRATION_2_3.migrate(db)

        assertEquals(2, executedSql.size)
        assertTrue(executedSql[0].contains("ALTER TABLE sightings ADD COLUMN hasLocation"))
        assertTrue(executedSql[1].contains("UPDATE sightings SET hasLocation = 0"))
    }
}
