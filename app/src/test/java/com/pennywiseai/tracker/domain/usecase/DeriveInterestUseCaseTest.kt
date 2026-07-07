package com.pennywiseai.tracker.domain.usecase

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the interest-derivation decision core (subaccounts plan T1.2).
 * The repository-facing orchestration is thin by design; every money
 * semantic lives in [decideInterest].
 */
class DeriveInterestUseCaseTest {

    private fun decide(
        derived: String,
        observed: String,
        isManual: Boolean = true,
        accountType: String? = "SAVINGS",
        isCreditCard: Boolean = false,
        alreadyToday: Boolean = false,
    ) = decideInterest(
        isManualAccount = isManual,
        accountType = accountType,
        isCreditCard = isCreditCard,
        derivedBalance = BigDecimal(derived),
        observedBalance = BigDecimal(observed),
        alreadyRecordedToday = alreadyToday,
    )

    @Test
    fun `positive delta becomes an interest record of exactly the delta`() {
        val decision = decide(derived = "500.00", observed = "502.15")
        assertEquals(InterestDecision.RecordInterest(BigDecimal("2.15")), decision)
    }

    @Test
    fun `negative delta is surfaced as a shortfall, never fabricated`() {
        val decision = decide(derived = "500.00", observed = "490.00")
        assertEquals(InterestDecision.Shortfall(BigDecimal("10.00")), decision)
    }

    @Test
    fun `zero delta is a no-op`() {
        assertEquals(InterestDecision.NoChange, decide(derived = "500.00", observed = "500.00"))
    }

    @Test
    fun `second observation the same day is dropped by idempotency`() {
        val decision = decide(derived = "500.00", observed = "502.15", alreadyToday = true)
        assertEquals(InterestDecision.AlreadyRecordedToday, decision)
    }

    @Test
    fun `same-day transfer in is not interest`() {
        // The repository's signed sum already includes transfer legs, so a
        // RM100 transfer-in raises DERIVED to 600 — observation 600 means
        // no interest, not RM100 of income.
        assertEquals(InterestDecision.NoChange, decide(derived = "600.00", observed = "600.00"))
    }

    @Test
    fun `INVESTMENT accounts are hard-excluded — market movement is never income`() {
        val decision = decide(derived = "1000.00", observed = "1150.00", accountType = "INVESTMENT")
        assertEquals(InterestDecision.NotEligible, decision)
    }

    @Test
    fun `non-manual and credit-card accounts are not eligible`() {
        assertEquals(
            InterestDecision.NotEligible,
            decide(derived = "500.00", observed = "510.00", isManual = false)
        )
        assertEquals(
            InterestDecision.NotEligible,
            decide(derived = "500.00", observed = "510.00", isCreditCard = true)
        )
    }

    @Test
    fun `interest hash is one per account per day`() {
        val date = LocalDate.parse("2026-07-08")
        assertEquals("interest-Boost Jar-JAR1-2026-07-08", interestHash("Boost Jar", "JAR1", date))
        // Different day → different hash (a new observation may record again).
        assertEquals(
            "interest-Boost Jar-JAR1-2026-07-09",
            interestHash("Boost Jar", "JAR1", date.plusDays(1))
        )
    }
}
