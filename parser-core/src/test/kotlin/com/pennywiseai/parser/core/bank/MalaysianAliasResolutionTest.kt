package com.pennywiseai.parser.core.bank

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Pins single-parser factory resolution for every Malaysian notification
 * alias and SMS sender (LESSONS 2026-07-07). TBankParser's substring
 * canHandle used to win "BoostBank"/"RytBank" (both contain "TBANK") because
 * T-Bank registers earlier — which silently disabled the notification
 * listener's cross-channel dedup and Boost self-transfer reclassification.
 *
 * Any new parser whose canHandle overlaps one of these aliases turns this
 * suite red immediately instead of failing silently on the device. Aliases
 * must stay in sync with `app/.../receiver/BankNotificationConfig.kt`.
 */
class MalaysianAliasResolutionTest {

    private val expectedResolution = mapOf(
        // Notification aliases (BankNotificationConfig)
        "BoostBank" to "Boost Bank",
        "Maybank2u" to "Maybank2u",
        "GXBank" to "GXBank",
        "PublicBank" to "Public Bank",
        "TNG" to "TNG eWallet",
        "RytBank" to "Ryt Bank",
        "ShopeePay" to "ShopeePay",
        // UOB credit-card SMS sender ids
        "67425" to "UOB",
        "66300" to "UOB",
        // Time Internet receipt shortcode — TimeParser records the payment
        // against the UOB card the bill auto-charges (see TimeParser KDoc)
        "62003" to "UOB",
    )

    @TestFactory
    fun `every Malaysian alias resolves to its own parser`(): List<DynamicTest> =
        expectedResolution.map { (alias, expectedBank) ->
            DynamicTest.dynamicTest("$alias -> $expectedBank") {
                val parser = BankParserFactory.getParser(alias)
                assertEquals(
                    expectedBank,
                    parser?.getBankName(),
                    "Alias '$alias' resolved to '${parser?.getBankName()}' — " +
                        "another parser's canHandle is shadowing it (check registration order and word boundaries)."
                )
            }
        }
}
