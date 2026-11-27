package com.example.bluetoothtracker.domain.model

import com.example.bluetoothtracker.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    @Test
    fun `all four themes have distinct style and label resources`() {
        val themes = AppTheme.entries
        assertEquals(4, themes.size)
        assertEquals(4, themes.map { it.themeResId }.toSet().size)
        assertEquals(4, themes.map { it.labelRes }.toSet().size)
        themes.forEach { assertNotEquals(0, it.themeResId) }
    }

    @Test
    fun `shouldRecreateForTheme prevents duplicate recreation when theme unchanged`() {
        AppTheme.entries.forEach { theme ->
            assertFalse(MainActivity.shouldRecreateForTheme(theme, theme))
        }
        assertTrue(MainActivity.shouldRecreateForTheme(AppTheme.ORANGE, AppTheme.BLUE))
        assertTrue(MainActivity.shouldRecreateForTheme(AppTheme.ORANGE, AppTheme.BLACK))
        assertTrue(MainActivity.shouldRecreateForTheme(AppTheme.BLACK, AppTheme.GREEN))
        assertTrue(MainActivity.shouldRecreateForTheme(AppTheme.GREEN, AppTheme.BLUE))
    }

    @Test
    fun `theme enum valueOf round-trips all four persisted theme names`() {
        listOf("BLUE", "ORANGE", "BLACK", "GREEN").forEach { name ->
            assertEquals(name, AppTheme.valueOf(name).name)
        }
    }
}
