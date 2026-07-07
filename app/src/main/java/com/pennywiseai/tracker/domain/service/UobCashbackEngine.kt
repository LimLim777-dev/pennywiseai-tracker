package com.pennywiseai.tracker.domain.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * UOB ONE Card cash-rebate engine (plan: docs/plans/2026-07-06-uob-one-cashback.md).
 *
 * Pure JVM — no Android/DB dependencies. Callers feed it REBATE-ELIGIBLE
 * transactions only (T&C exclusions — utility bills, JomPAY, PIB, top-ups
 * except GrabPay, instalments, fees — are filtered upstream) with a rebate
 * category already attributed (merchant-name mapping is approximate; SMS
 * carries no MCC).
 *
 * Statement cycle: the statement date (e.g. the 17th) closes the month; a
 * cycle window is [cycleStartDay of M-1, cycleStartDay-1 of M]. Minimum
 * Retail Spend and rebates are computed on POSTING date, which the card
 * SMS does not carry — the engine estimates it as transactionDate +
 * [postingLagDays] and flags boundary transactions whose true cycle is
 * uncertain.
 */
enum class UobRebateCategory { PETROL, GROCERIES, DINING, GRAB, OTHERS }

data class UobTierConfig(
    /** Minimum Retail Spend per statement month to unlock the boosted rate. */
    val minRetailSpend: BigDecimal,
    /** Rate for PETROL/GROCERIES/DINING/GRAB once the minimum is met. */
    val boostedRate: BigDecimal,
    /** Rate below the minimum, and always for OTHERS. */
    val baseRate: BigDecimal,
    /** Max rebate per boosted category per statement month. */
    val perCategoryCap: BigDecimal,
) {
    companion object {
        /** ONE Classic (user's card): RM800 minimum, RM10 caps. */
        val CLASSIC = UobTierConfig(
            minRetailSpend = BigDecimal("800"),
            boostedRate = BigDecimal("0.10"),
            baseRate = BigDecimal("0.002"),
            perCategoryCap = BigDecimal("10"),
        )

        /** ONE Platinum (reference, in case of upgrade): RM1,500 / RM15. */
        val PLATINUM = UobTierConfig(
            minRetailSpend = BigDecimal("1500"),
            boostedRate = BigDecimal("0.10"),
            baseRate = BigDecimal("0.002"),
            perCategoryCap = BigDecimal("15"),
        )
    }
}

data class UobTxn(
    /** Transaction date (what the SMS carries — NOT the posting date). */
    val date: LocalDate,
    val amount: BigDecimal,
    val category: UobRebateCategory,
)

data class UobCycleWindow(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)
}

data class UobCategoryResult(
    val category: UobRebateCategory,
    val spend: BigDecimal,
    val rebate: BigDecimal,
    val capped: Boolean,
    /**
     * Spend still earning the boosted rate before this category's cap
     * ("sweet spot": RM100 per category on Classic). Zero when capped;
     * null when the boosted rate isn't active or category is OTHERS.
     */
    val boostedSpendRemaining: BigDecimal?,
)

data class UobCycleResult(
    val statementMonth: YearMonth,
    val window: UobCycleWindow,
    /** Eligible spend whose estimated posting date is confidently in-window. */
    val confirmedSpend: BigDecimal,
    /** Spend near a cycle boundary that may post into the adjacent cycle. */
    val uncertainSpend: BigDecimal,
    val thresholdMet: Boolean,
    /** RM still needed to unlock the boosted rate (ZERO once met). */
    val remainingToThreshold: BigDecimal,
    val categories: List<UobCategoryResult>,
    val totalRebate: BigDecimal,
)

class UobCashbackEngine(
    private val config: UobTierConfig = UobTierConfig.CLASSIC,
    /** First day of a cycle window; 18 → windows run 18th to 17th. */
    private val cycleStartDay: Int = 18,
    /** Empirical posting lag (2 days per the real Jun 2026 statement). */
    private val postingLagDays: Long = 2,
) {

    /** Cycle window that the given statement month closes. */
    fun windowFor(statementMonth: YearMonth): UobCycleWindow {
        val startMonth = statementMonth.minusMonths(1)
        val start = startMonth.atDay(minOf(cycleStartDay, startMonth.lengthOfMonth()))
        val end = statementMonth.atDay(minOf(cycleStartDay - 1, statementMonth.lengthOfMonth()))
        return UobCycleWindow(start, end)
    }

    /** Statement month whose window contains [postDate]. */
    fun statementMonthFor(postDate: LocalDate): YearMonth {
        val sameMonth = YearMonth.from(postDate)
        return if (postDate in windowFor(sameMonth)) sameMonth else sameMonth.plusMonths(1)
    }

    private fun estimatedPostDate(txn: UobTxn): LocalDate = txn.date.plusDays(postingLagDays)

    /**
     * A transaction is boundary-uncertain when its plausible posting range
     * [date, date + 2×lag] spans two cycle windows — the true cycle can only
     * be confirmed against the UOB app/statement.
     */
    fun isUncertain(txn: UobTxn): Boolean =
        statementMonthFor(txn.date) != statementMonthFor(txn.date.plusDays(2 * postingLagDays))

    /**
     * Evaluate one statement month. [txns] may span any date range; the
     * engine assigns by estimated posting date and computes rebates from
     * confirmed in-window spend only.
     */
    fun evaluate(statementMonth: YearMonth, txns: List<UobTxn>): UobCycleResult {
        val window = windowFor(statementMonth)

        val assigned = txns.filter { statementMonthFor(estimatedPostDate(it)) == statementMonth }
        val (uncertain, confirmed) = assigned.partition { isUncertain(it) }

        val confirmedSpend = confirmed.sumOf { it.amount }
        val uncertainSpend = uncertain.sumOf { it.amount }
        val thresholdMet = confirmedSpend >= config.minRetailSpend
        val remaining = (config.minRetailSpend - confirmedSpend).max(BigDecimal.ZERO)

        val categories = UobRebateCategory.entries.map { cat ->
            val spend = confirmed.filter { it.category == cat }.sumOf { it.amount }
            val boosted = thresholdMet && cat != UobRebateCategory.OTHERS
            val rate = if (boosted) config.boostedRate else config.baseRate
            // HALF_UP calibrated against real statements: APR 2026 dining
            // 134.20 × 0.2% = 0.2684 credited as RM0.27 (floor would give
            // 0.26), and a cross-statement Grab total of 76.27 × 10% =
            // 7.627 credited as RM7.63.
            val raw = (spend * rate).setScale(2, RoundingMode.HALF_UP)
            val capApplies = cat != UobRebateCategory.OTHERS
            val rebate = (if (capApplies) raw.min(config.perCategoryCap) else raw)
                .setScale(2, RoundingMode.HALF_UP)
            val capped = capApplies && raw > config.perCategoryCap
            val boostedRemaining = if (boosted) {
                val capSpend = config.perCategoryCap.divide(config.boostedRate, 2, RoundingMode.DOWN)
                (capSpend - spend).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN)
            } else null
            UobCategoryResult(cat, spend, rebate, capped, boostedRemaining)
        }

        return UobCycleResult(
            statementMonth = statementMonth,
            window = window,
            confirmedSpend = confirmedSpend,
            uncertainSpend = uncertainSpend,
            thresholdMet = thresholdMet,
            remainingToThreshold = remaining,
            categories = categories,
            totalRebate = categories.sumOf { it.rebate },
        )
    }
}

/**
 * Approximate merchant-name → rebate-category mapping (SMS carries no MCC).
 * Grocery chain names come verbatim from the UOB T&C; petrol brands and the
 * GRAB prefix from real statements. Dining cannot be inferred generically —
 * unmatched merchants fall to OTHERS and the UI must let the user reassign
 * (which should feed a persistent override, e.g. MerchantMapping).
 */
object UobCategoryMapper {

    private val GROCERY_KEYWORDS = listOf(
        "AEON BIG", "AEON SUPERMARKET", "AEON CO", "BEN'S INDEPENDENT",
        "COLD STORAGE", "ECONSAVE", "EVERISE", "GIANT", "JAYA GROCER",
        "LOTUS'S", "LOTUS S", "MERCATO", "MYDIN", "SERVAY", "VILLAGE GROCER",
        "MAXVALUE", "THE FOOD MERCHANT",
    )

    private val PETROL_KEYWORDS = listOf(
        "SETEL", "PETRONAS", "SHELL", "PETRON", "CALTEX", "BHPETROL", "BHP ",
    )

    // Calibrated against the APR–JUN 2026 statements: generic F&B words plus
    // the statement-observed merchants UOB's dining rebate line covers
    // (APR's dining rebate reconciles to a single PEPPER WESTERN charge;
    // JUN's capped dining is carried by PARA THAI). Kept conservative —
    // ambiguous names (generic "SDN BHD" merchants) stay OTHERS, because a
    // wrong DINING guess overstates the 10% rebate estimate.
    private val DINING_KEYWORDS = listOf(
        "COFFEE", "CAFE", "KOPITIAM", "SUSHI", "KITCHEN", "RESTAURANT",
        "RESTORAN", "BISTRO", "BAKERY", "PEPPER WESTERN", "PARA THAI",
        "ZUS",
    )

    fun categorize(merchantName: String, overrides: Map<String, UobRebateCategory> = emptyMap()): UobRebateCategory {
        val name = merchantName.trim().uppercase()
        overrides[name]?.let { return it }
        return when {
            name.startsWith("GRAB") -> UobRebateCategory.GRAB
            PETROL_KEYWORDS.any { name.contains(it) } -> UobRebateCategory.PETROL
            GROCERY_KEYWORDS.any { name.contains(it) } -> UobRebateCategory.GROCERIES
            DINING_KEYWORDS.any { name.contains(it) } -> UobRebateCategory.DINING
            else -> UobRebateCategory.OTHERS
        }
    }
}
