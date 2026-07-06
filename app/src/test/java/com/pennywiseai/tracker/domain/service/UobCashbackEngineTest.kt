package com.pennywiseai.tracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Engine tests for the UOB ONE cashback plan (T-C2). Synthetic fixtures with
 * exact expected outputs; real-statement reconciliation (May/Jun 2026 rebate
 * lines) is a separate calibration task because category attribution from
 * merchant text is approximate — see the plan doc.
 */
class UobCashbackEngineTest {

    private val engine = UobCashbackEngine() // CLASSIC, startDay 18, lag 2

    private fun txn(date: String, amount: String, cat: UobRebateCategory) =
        UobTxn(LocalDate.parse(date), BigDecimal(amount), cat)

    // ---- cycle windows ----

    @Test
    fun `window for a statement month runs 18th to 17th`() {
        val w = engine.windowFor(YearMonth.of(2026, 7))
        assertEquals(LocalDate.parse("2026-06-18"), w.start)
        assertEquals(LocalDate.parse("2026-07-17"), w.endInclusive)
    }

    @Test
    fun `window start clamps in short months when startDay exceeds month length`() {
        val e = UobCashbackEngine(cycleStartDay = 31)
        val w = e.windowFor(YearMonth.of(2026, 3)) // Feb 2026 has 28 days
        assertEquals(LocalDate.parse("2026-02-28"), w.start)
        assertEquals(LocalDate.parse("2026-03-30"), w.endInclusive)
    }

    @Test
    fun `posting on the 17th belongs to the closing statement, 18th to the next`() {
        assertEquals(YearMonth.of(2026, 7), engine.statementMonthFor(LocalDate.parse("2026-07-17")))
        assertEquals(YearMonth.of(2026, 8), engine.statementMonthFor(LocalDate.parse("2026-07-18")))
    }

    // ---- threshold + rates ----

    @Test
    fun `below minimum spend everything earns the base rate`() {
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-06-20", "100.00", UobRebateCategory.DINING),
                txn("2026-06-25", "200.00", UobRebateCategory.OTHERS),
            )
        )
        assertFalse(result.thresholdMet)
        assertEquals(BigDecimal("500.00"), result.remainingToThreshold)
        val dining = result.categories.first { it.category == UobRebateCategory.DINING }
        assertEquals(BigDecimal("0.20"), dining.rebate) // 100 × 0.2%
    }

    @Test
    fun `above minimum spend boosted categories earn 10 percent and OTHERS stays base`() {
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-06-20", "80.00", UobRebateCategory.PETROL),
                txn("2026-06-21", "39.20", UobRebateCategory.GRAB),
                txn("2026-06-25", "910.00", UobRebateCategory.OTHERS),
            )
        )
        assertTrue(result.thresholdMet)
        assertEquals(BigDecimal("8.00"), result.categories.first { it.category == UobRebateCategory.PETROL }.rebate)
        assertEquals(BigDecimal("3.92"), result.categories.first { it.category == UobRebateCategory.GRAB }.rebate)
        assertEquals(BigDecimal("1.82"), result.categories.first { it.category == UobRebateCategory.OTHERS }.rebate)
    }

    @Test
    fun `boosted category rebate is capped and sweet spot reports zero remaining`() {
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-06-20", "203.90", UobRebateCategory.DINING),
                txn("2026-06-25", "800.00", UobRebateCategory.OTHERS),
            )
        )
        val dining = result.categories.first { it.category == UobRebateCategory.DINING }
        assertEquals(BigDecimal("10.00"), dining.rebate) // 20.39 → capped at 10
        assertTrue(dining.capped)
        assertEquals(BigDecimal("0.00"), dining.boostedSpendRemaining)
    }

    @Test
    fun `sweet spot reports remaining boosted spend before the cap`() {
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-06-20", "80.00", UobRebateCategory.DINING),
                txn("2026-06-25", "800.00", UobRebateCategory.OTHERS),
            )
        )
        val dining = result.categories.first { it.category == UobRebateCategory.DINING }
        // Cap RM10 at 10% → RM100 of boosted spend; RM80 used → RM20 left.
        assertEquals(BigDecimal("20.00"), dining.boostedSpendRemaining)
    }

    @Test
    fun `rebate rounding floors to the cent`() {
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-06-20", "91.75", UobRebateCategory.GROCERIES),
                txn("2026-06-25", "800.00", UobRebateCategory.OTHERS),
            )
        )
        val grocery = result.categories.first { it.category == UobRebateCategory.GROCERIES }
        assertEquals(BigDecimal("9.17"), grocery.rebate) // 9.175 floors, never rounds up
    }

    // ---- posting-lag assignment + uncertainty ----

    @Test
    fun `transaction near cycle close is uncertain and excluded from confirmed spend`() {
        // txn on the 14th, lag 2: estimated post 16th (this cycle) but the
        // plausible posting range 14th–18th crosses the boundary → uncertain.
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-07-14", "50.00", UobRebateCategory.DINING),
                txn("2026-07-01", "100.00", UobRebateCategory.OTHERS),
            )
        )
        assertEquals(BigDecimal("100.00"), result.confirmedSpend)
        assertEquals(BigDecimal("50.00"), result.uncertainSpend)
    }

    @Test
    fun `transaction dated after cycle close is assigned to the next statement month`() {
        // txn on the 16th, estimated post 18th → assigned (uncertain) to August.
        val august = engine.evaluate(
            YearMonth.of(2026, 8),
            listOf(txn("2026-07-16", "50.00", UobRebateCategory.DINING))
        )
        assertEquals(BigDecimal("50.00"), august.uncertainSpend)

        val july = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(txn("2026-07-16", "50.00", UobRebateCategory.DINING))
        )
        assertEquals(BigDecimal.ZERO, july.confirmedSpend + july.uncertainSpend)
    }

    @Test
    fun `mid-cycle transaction is certain`() {
        val t = txn("2026-07-01", "10.00", UobRebateCategory.OTHERS)
        assertFalse(engine.isUncertain(t))
    }

    // ---- merchant mapping ----

    @Test
    fun `mapper covers statement-observed merchants`() {
        assertEquals(UobRebateCategory.PETROL, UobCategoryMapper.categorize("SETEL"))
        assertEquals(UobRebateCategory.GRAB, UobCategoryMapper.categorize("GRAB-EC"))
        assertEquals(UobRebateCategory.GROCERIES, UobCategoryMapper.categorize("LOTUS'S PENANG SG DUA"))
        assertEquals(UobRebateCategory.GROCERIES, UobCategoryMapper.categorize("AEON CO-QUEENSBAY MALL"))
        assertEquals(UobRebateCategory.OTHERS, UobCategoryMapper.categorize("BOOKMYSHOW - ECOM"))
    }

    @Test
    fun `mapper overrides win over keyword rules`() {
        val overrides = mapOf("ZUS COFFEE" to UobRebateCategory.DINING)
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("Zus Coffee", overrides))
    }
}
