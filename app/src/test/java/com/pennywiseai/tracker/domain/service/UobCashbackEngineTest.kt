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

    // ---- real-statement acceptance fixtures (MAY/JUN 2026, 10% tier) ----

    @Test
    fun `JUN 2026 statement reproduces all five real rebate lines at the boosted tier`() {
        // Real 17 JUN 26 statement: retail purchase RM1,339.85 ≥ 800 → 10%
        // tier. Rebate lines re-read from the statement file 2026-07-08 (the
        // plan doc's "Grocery 6.28" was a transcription error — the credited
        // line is 8.28). Per-category eligible spends reconstructed from the
        // credited rebate lines (rebate ÷ rate); dining is capped so its
        // exact spend isn't recoverable — it takes the remainder to make the
        // statement's retail total, and any value ≥ RM100 caps at RM10:
        //   dining    227.85  → capped     → 10.00
        //   petrol     80.00  → 10%        →  8.00
        //   groceries  82.80  → 10%        →  8.28
        //   grab       39.20  → 10%        →  3.92
        //   others    910.00  → 0.2%       →  1.82  (OTHERS stays base at tier)
        val result = engine.evaluate(
            YearMonth.of(2026, 6),
            listOf(
                txn("2026-05-25", "227.85", UobRebateCategory.DINING),
                txn("2026-05-25", "80.00", UobRebateCategory.PETROL),
                txn("2026-05-26", "82.80", UobRebateCategory.GROCERIES),
                txn("2026-06-01", "39.20", UobRebateCategory.GRAB),
                txn("2026-06-01", "910.00", UobRebateCategory.OTHERS),
            )
        )
        assertTrue(result.thresholdMet)
        assertEquals(BigDecimal("1339.85"), result.confirmedSpend)
        fun cat(c: UobRebateCategory) = result.categories.first { it.category == c }
        assertEquals(BigDecimal("10.00"), cat(UobRebateCategory.DINING).rebate)
        assertTrue(cat(UobRebateCategory.DINING).capped)
        assertEquals(BigDecimal("8.00"), cat(UobRebateCategory.PETROL).rebate)
        assertEquals(BigDecimal("8.28"), cat(UobRebateCategory.GROCERIES).rebate)
        assertEquals(BigDecimal("3.92"), cat(UobRebateCategory.GRAB).rebate)
        assertEquals(BigDecimal("1.82"), cat(UobRebateCategory.OTHERS).rebate)
        assertEquals(BigDecimal("32.02"), result.totalRebate)
    }

    @Test
    fun `MAY 2026 statement reproduces the triple-capped sweet spot and boundary carryover`() {
        // Real 17 MAY 26 statement: retail purchase RM1,008.49 ≥ 800.
        // Petrol, grocery AND dining all credited RM10.00 (capped) — the
        // exact split among the three isn't recoverable from the rebate
        // lines (any split with each ≥ RM100 caps identically); the fixture
        // splits their RM387.22 remainder arbitrarily. The uncapped lines
        // are exact:
        //   grab    76.27  → 7.627 → 7.63  (half-up at the 10% tier; the
        //     76.27 is the verified carryover: 19.80 + 47.47 + 9.00 dated
        //     16 APR posted into MAY — the boundary-divergence evidence)
        //   others 545.00  → 1.09  (1.08 in the first transcription was a
        //     typo — corrected against the statement file)
        val result = engine.evaluate(
            YearMonth.of(2026, 5),
            listOf(
                txn("2026-04-25", "120.00", UobRebateCategory.PETROL),
                txn("2026-04-25", "130.00", UobRebateCategory.GROCERIES),
                txn("2026-04-26", "137.22", UobRebateCategory.DINING),
                txn("2026-05-01", "76.27", UobRebateCategory.GRAB),
                txn("2026-05-01", "545.00", UobRebateCategory.OTHERS),
            )
        )
        assertTrue(result.thresholdMet)
        assertEquals(BigDecimal("1008.49"), result.confirmedSpend)
        fun cat(c: UobRebateCategory) = result.categories.first { it.category == c }
        assertEquals(BigDecimal("10.00"), cat(UobRebateCategory.PETROL).rebate)
        assertEquals(BigDecimal("10.00"), cat(UobRebateCategory.GROCERIES).rebate)
        assertEquals(BigDecimal("10.00"), cat(UobRebateCategory.DINING).rebate)
        assertTrue(cat(UobRebateCategory.PETROL).capped)
        assertTrue(cat(UobRebateCategory.GROCERIES).capped)
        assertTrue(cat(UobRebateCategory.DINING).capped)
        assertEquals(BigDecimal("7.63"), cat(UobRebateCategory.GRAB).rebate)
        assertEquals(BigDecimal("1.09"), cat(UobRebateCategory.OTHERS).rebate)
        assertEquals(BigDecimal("38.72"), result.totalRebate)
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
    fun `mapper covers statement-verified dining merchants`() {
        // PEPPER WESTERN reconciles exactly to APR's dining rebate line;
        // PARA THAI carries JUN's capped dining. The rest are the user's
        // recurring F&B merchants matched by generic keywords.
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("PEPPER WESTERN - PASTA"))
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("PARA THAI QUEENSBAY MALL"))
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("ZUS COFFEE"))
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("PYX*DAILY COFFEE"))
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("SUSHI KING-QUEENSBAY MALL"))
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("ROCK TILL DAWN KITCHEN"))
        // UOB's own rebate attribution puts these in OTHERS — don't "improve" them:
        // Suiwah (supermarket) appeared in APR's reconciled Others sum.
        assertEquals(UobRebateCategory.OTHERS, UobCategoryMapper.categorize("SUIWAH HOLDINGS"))
        assertEquals(UobRebateCategory.OTHERS, UobCategoryMapper.categorize("MR DIY (H)-QMP"))
    }

    @Test
    fun `mapper overrides win over keyword rules`() {
        val overrides = mapOf("ZUS COFFEE" to UobRebateCategory.DINING)
        assertEquals(UobRebateCategory.DINING, UobCategoryMapper.categorize("Zus Coffee", overrides))
    }
}
