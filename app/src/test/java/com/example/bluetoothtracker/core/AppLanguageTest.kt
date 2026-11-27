package com.example.bluetoothtracker.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `fromTag resolves exact and base language tags`() {
        assertEquals(AppLanguage.ENGLISH_UK, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.ENGLISH_UK, AppLanguage.fromTag("en_US"))
        assertEquals(AppLanguage.URDU, AppLanguage.fromTag("ur"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.fromTag("fa"))
    }

    @Test
    fun `fromTag falls back to DEFAULT for null or unknown tags`() {
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag("xx-UNKNOWN"))
    }
}
