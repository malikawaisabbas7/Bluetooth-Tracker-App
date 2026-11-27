package com.example.bluetoothtracker.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLanguageReturnTest {

    @Test
    fun `one-time pending navigation flag is consumed at most once`() {
        val flag = PendingLanguageReturn.OneTimeFlag()
        assertFalse(flag.consume())

        flag.mark()
        assertTrue(flag.consume())
        assertFalse(flag.consume())
    }
}
