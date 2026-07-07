package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Real receipt sample from 2026-05-21 (W1 precondition: never fabricate
 * samples). The Time service account number is masked; amount/date/wording
 * are verbatim. Bank name is "UOB" by design — see TimeParser KDoc.
 */
class TimeParserTest {

    private val parser = TimeParser()

    private val realReceipt = """
        RM0 Time:
        Thanks! We've received your payment.
        A/C No: 123456789012
        Amt: RM102.80
        Date: 21 May 2026
        Download the Time Fibre Home app to check your updated balance. TQ.
    """.trimIndent()

    @TestFactory
    fun `time payment receipts`(): List<DynamicTest> = ParserTestUtils.runTestSuite(
        parser,
        testCases = listOf(
            ParserTestCase(
                name = "payment received receipt parses as UOB card expense",
                message = realReceipt,
                sender = "62003",
                expected = ExpectedTransaction(
                    amount = BigDecimal("102.80"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "Time Fibre",
                    // Must stay null: the 12-digit Time service account in
                    // the body must NOT mint a bogus UOB account.
                    accountLast4 = null,
                ),
            ),
            ParserTestCase(
                name = "non-payment Time message is rejected",
                message = "RM0 Time: Your bill for June 2026 is ready. View it in the Time Fibre Home app. TQ.",
                sender = "62003",
                shouldParse = false,
            ),
        ),
        handleCases = listOf(
            "62003" to true,
            "TIME" to true,
            "Time" to true,
            // Substring senders must NOT match (T-Bank lesson, 2026-07-07).
            "MAYBANKTIME" to false,
            "67425" to false,
        ),
        suiteName = "TimeParser",
    )
}
