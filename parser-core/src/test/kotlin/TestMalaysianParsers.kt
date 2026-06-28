import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MalaysianParsersTest {

    @TestFactory
    fun `Boost Bank parser`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Boost Bank",
                sender = "BoostBank",
                currency = "MYR",
                message = "Your transfer of RM50.00 to JOHN DOE is successful.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE"
                ),
                shouldHandle = true,
                description = "Boost outgoing transfer to third party"
            ),
            SimpleTestCase(
                bankName = "Boost Bank",
                sender = "BoostBank",
                currency = "MYR",
                message = "Your transfer of RM100.00 to MAH GUO REN is successful.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "MYR",
                    type = TransactionType.TRANSFER,
                    merchant = "MAH GUO REN"
                ),
                shouldHandle = true,
                description = "Boost outgoing self-transfer (owner name → TRANSFER)"
            ),
            SimpleTestCase(
                bankName = "Boost Bank",
                sender = "BoostBank",
                currency = "MYR",
                message = "You've received RM200.00 into your account ending **5678.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "MYR",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678"
                ),
                shouldHandle = true,
                description = "Boost incoming (no sender name → INCOME; cross-bank reclassification is app-layer logic)"
            ),
            SimpleTestCase(
                bankName = "Boost Bank",
                sender = "BoostBank",
                currency = "MYR",
                message = "Your Jar transfer of RM30.00 is successful.",
                shouldParse = false,
                shouldHandle = true,
                description = "Boost Jar internal transfer → skip"
            )
        )
        return ParserTestUtils.runFactoryTestSuite(cases, "Boost Bank")
    }

    @TestFactory
    fun `Maybank MAE parser`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Maybank2u",
                sender = "Maybank2u",
                currency = "MYR",
                message = "Transfer of RM500.00 to JANE GOH is successful. Ref: TXN123456.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "JANE GOH"
                ),
                shouldHandle = true,
                description = "MAE outgoing transfer"
            ),
            SimpleTestCase(
                bankName = "Maybank2u",
                sender = "Maybank2u",
                currency = "MYR",
                message = "FPX Payment of RM150.00 to SHOPEE ONLINE is successful.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "SHOPEE ONLINE"
                ),
                shouldHandle = true,
                description = "MAE FPX payment"
            )
        )
        return ParserTestUtils.runFactoryTestSuite(cases, "Maybank MAE")
    }

    @TestFactory
    fun `GXBank parser`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "GXBank",
                sender = "GXBank",
                currency = "MYR",
                message = "You've sent RM30.00 to AHMAD BIN ALI. Balance: RM970.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30.00"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true,
                description = "GXBank outgoing"
            ),
            SimpleTestCase(
                bankName = "GXBank",
                sender = "GXBank",
                currency = "MYR",
                message = "You've received RM5.00 from MAH GUO REN.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "MYR",
                    type = TransactionType.TRANSFER
                ),
                shouldHandle = true,
                description = "GXBank incoming from self (owner name → TRANSFER)"
            ),
            SimpleTestCase(
                bankName = "GXBank",
                sender = "GXBank",
                currency = "MYR",
                message = "You've received RM50.00 from TAN AH KOW.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "MYR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true,
                description = "GXBank incoming from third party → INCOME"
            )
        )
        return ParserTestUtils.runFactoryTestSuite(cases, "GXBank")
    }

    @TestFactory
    fun `TNG eWallet parser`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "TNG eWallet",
                sender = "TNG",
                currency = "MYR",
                message = "You have sent RM25.00 to LEE CHONG WEI. Your TNG eWallet balance is RM75.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "LEE CHONG WEI"
                ),
                shouldHandle = true,
                description = "TNG outgoing transfer"
            ),
            SimpleTestCase(
                bankName = "TNG eWallet",
                sender = "TNG",
                currency = "MYR",
                message = "You have received RM10.00 from SITI AMINAH. Your TNG eWallet balance is RM110.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "MYR",
                    type = TransactionType.INCOME,
                    merchant = "SITI AMINAH"
                ),
                shouldHandle = true,
                description = "TNG incoming from third party"
            )
        )
        return ParserTestUtils.runFactoryTestSuite(cases, "TNG eWallet")
    }

    @TestFactory
    fun `Ryt Bank parser`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Ryt Bank",
                sender = "RytBank",
                currency = "MYR",
                message = "Payment of RM88.00 to UNIFI has been made successfully.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("88.00"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "UNIFI"
                ),
                shouldHandle = true,
                description = "Ryt Bank payment"
            )
        )
        return ParserTestUtils.runFactoryTestSuite(cases, "Ryt Bank")
    }

    @TestFactory
    fun `UOB Card SMS parser`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "UOB",
                sender = "67425",
                currency = "MYR",
                message = "UOB: A transaction of RM45.90 was made at GRAB on your card ending 1234 on 27/06/2026. If unauthorised, call 1800-88-2121.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.90"),
                    currency = "MYR",
                    type = TransactionType.EXPENSE,
                    merchant = "GRAB",
                    accountLast4 = "1234"
                ),
                shouldHandle = true,
                description = "UOB credit card SMS transaction"
            )
        )
        return ParserTestUtils.runFactoryTestSuite(cases, "UOB Card")
    }
}
