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
    fun `rebate rounding is half-up to the cent`() {
        // Calibrated 2026-07-07 against the real APR 2026 statement: dining
        // 134.20 × 0.2% = 0.2684 was credited as RM0.27 — UOB rounds
        // half-up, not floor (the engine originally floored).
        val result = engine.evaluate(
            YearMonth.of(2026, 7),
            listOf(
                txn("2026-06-20", "91.75", UobRebateCategory.GROCERIES),
                txn("2026-06-25", "800.00", UobRebateCategory.OTHERS),
            )
        )
        val grocery = result.categories.first { it.category == UobRebateCategory.GROCERIES }
        assertEquals(BigDecimal("9.18"), grocery.rebate) // 9.175 rounds half-up
    }

    // ---- real-statement acceptance fixture (APR 2026, below-threshold month) ----

    @Test
    fun `APR 2026 statement reproduces all five real rebate lines at the base rate`() {
        // Reconstructed from the real 17 APR 26 statement. The two Grab
        // purchases dated 16 APR posted after the window and their rebate
        // landed in MAY (verified: MAY's Grab rebate 7.63 = 10% of
        // 19.80 + 47.47 + 9.00, half-up) — they are excluded here.
        // Category spends (single txns; mid-window dates avoid boundary
        // uncertainty — real dates spanned 18 MAR–13 APR):
        //   dining    134.20  → 0.2684 → 0.27  (discriminates half-up vs floor)
        //   petrol    100.00  → 0.20
        //   groceries  26.70  → 0.0534 → 0.05
        //   grab       16.70  → 0.0334 → 0.03
        //   others    455.90  → 0.9118 → 0.91  (includes the RM300 insurance
        //     premium — proving insurance DOES count toward rebate/min spend)
        // Eligible spend 733.50 < 800 → threshold NOT met, base rate all round.
        val result = engine.evaluate(
            YearMonth.of(2026, 4),
            listOf(
                txn("2026-03-25", "134.20", UobRebateCategory.DINING),
                txn("2026-03-25", "100.00", UobRebateCategory.PETROL),
                txn("2026-03-26", "26.70", UobRebateCategory.GROCERIES),
                txn("2026-04-01", "16.70", UobRebateCategory.GRAB),
                txn("2026-04-01", "455.90", UobRebateCategory.OTHERS),
            )
        )
        assertFalse(result.thresholdMet)
        assertEquals(BigDecimal("733.50"), result.confirmedSpend)
        fun rebate(cat: UobRebateCategory) = result.categories.first { it.category == cat }.rebate
        assertEquals(BigDecimal("0.27"), rebate(UobRebateCategory.DINING))
        assertEquals(BigDecimal("0.20"), rebate(UobRebateCategory.PETROL))
        assertEquals(BigDecimal("0.05"), rebate(UobRebateCategory.GROCERIES))
        assertEquals(BigDecimal("0.03"), rebate(UobRebateCategory.GRAB))
        assertEquals(BigDecimal("0.91"), rebate(UobRebateCategory.OTHERS))
        assertEquals(BigDecimal("1.46"), result.totalRebate)
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
