package com.example.bluetoothtracker.data.billing

import com.example.bluetoothtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PremiumStateTest {

    @Test
    fun `all six PremiumUiState variants are distinct and carry expected data`() {
        val offer = PremiumOffer(price = "$2.99", periodRes = R.string.period_week, trialDays = 3)
        val states: List<PremiumUiState> = listOf(
            PremiumUiState.Loading,
            PremiumUiState.ProductAvailable(offer),
            PremiumUiState.ProductUnavailable,
            PremiumUiState.Error(R.string.billing_unavailable),
            PremiumUiState.PurchasePending,
            PremiumUiState.Active(offer)
        )

        assertEquals(6, states.distinct().size)
        assertEquals("$2.99", (states[1] as PremiumUiState.ProductAvailable).offer.price)
        assertEquals(R.string.billing_unavailable, (states[3] as PremiumUiState.Error).messageRes)
        assertNotEquals(PremiumUiState.ProductUnavailable, PremiumUiState.PurchasePending)
    }
}
