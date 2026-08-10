package com.pennywiseai.tracker.presentation.add

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantSuggestionsTest {

    // Ordered by usage, as the DAO returns them.
    private val history = listOf(
        "SETEL", "GRAB-EC", "ZUS COFFEE", "OFFSET CAFE", "Setel Ipoh", "MR DIY"
    )

    @Test
    fun `first typed character already narrows the list`() {
        val result = filterMerchantSuggestions("s", history)
        // Prefix hits first, then anything containing an "s" (ZUS, OFFSET),
        // each group keeping the usage order it arrived in.
        assertEquals(listOf("SETEL", "Setel Ipoh", "ZUS COFFEE", "OFFSET CAFE"), result)
    }

    @Test
    fun `prefix matches rank above mid-string matches`() {
        val result = filterMerchantSuggestions("set", history)
        // SETEL and Setel Ipoh start with it; OFFSET CAFE only contains it.
        assertEquals(listOf("SETEL", "Setel Ipoh", "OFFSET CAFE"), result)
    }

    @Test
    fun `matching is case-insensitive both ways`() {
        assertTrue(filterMerchantSuggestions("ZUS", history).contains("ZUS COFFEE"))
        assertTrue(filterMerchantSuggestions("zus", history).contains("ZUS COFFEE"))
        assertTrue(filterMerchantSuggestions("SETEL IPOH", history).isEmpty())
    }

    @Test
    fun `usage order is preserved within a rank`() {
        // SETEL outranks Setel Ipoh in the input list and must stay first.
        assertEquals("SETEL", filterMerchantSuggestions("se", history).first())
    }

    @Test
    fun `an exact match suggests nothing — the user already typed it`() {
        assertEquals(emptyList<String>(), filterMerchantSuggestions("MR DIY", history))
        assertEquals(emptyList<String>(), filterMerchantSuggestions("mr diy", history))
    }

    @Test
    fun `empty or blank query suggests nothing`() {
        assertEquals(emptyList<String>(), filterMerchantSuggestions("", history))
        assertEquals(emptyList<String>(), filterMerchantSuggestions("   ", history))
    }

    @Test
    fun `no match suggests nothing`() {
        assertEquals(emptyList<String>(), filterMerchantSuggestions("xyz", history))
    }

    @Test
    fun `results are capped`() {
        val many = (1..20).map { "SHOP $it" }
        assertEquals(6, filterMerchantSuggestions("shop", many).size)
        assertEquals(3, filterMerchantSuggestions("shop", many, limit = 3).size)
    }
}
