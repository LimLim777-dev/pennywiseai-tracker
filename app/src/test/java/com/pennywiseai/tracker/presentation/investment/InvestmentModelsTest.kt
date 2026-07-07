package com.pennywiseai.tracker.presentation.investment

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvestmentModelsTest {

    private fun account(bankName: String, currency: String, balance: String) =
        AccountBalanceEntity(
            bankName = bankName,
            accountLast4 = "INVESTMENT",
            balance = BigDecimal(balance),
            timestamp = LocalDateTime.now(),
            accountType = "INVESTMENT",
            currency = currency,
        )

    private fun txn(bankName: String, currency: String, amount: String) =
        TransactionEntity(
            amount = BigDecimal(amount),
            merchantName = bankName,
            category = "Investments",
            transactionType = TransactionType.INVESTMENT,
            dateTime = LocalDateTime.now(),
            bankName = bankName,
            currency = currency,
            transactionHash = "test-$bankName-$currency-$amount",
        )

    @Test
    fun `contributions sum into invested and drive unrealized P and L`() {
        val rows = buildInvestmentRows(
            accounts = listOf(account("PMO Plus", "MYR", "1250.00")),
            investmentTransactions = listOf(
                txn("PMO Plus", "MYR", "500.00"),
                txn("PMO Plus", "MYR", "600.00"),
            ),
        )
        assertEquals(BigDecimal("1100.00"), rows.single().invested)
        assertEquals(BigDecimal("150.00"), rows.single().unrealizedPl)
    }

    @Test
    fun `account with no captured contributions shows value only`() {
        val rows = buildInvestmentRows(
            accounts = listOf(account("RHB Trade Smart", "MYR", "9000.00")),
            investmentTransactions = emptyList(),
        )
        assertNull(rows.single().invested)
        assertNull(rows.single().unrealizedPl)
        assertEquals(BigDecimal("9000.00"), rows.single().currentValue)
    }

    @Test
    fun `same platform in two currencies never mixes amounts`() {
        // MooMoo holds USD and MYR as two account rows — a USD contribution
        // must not count toward the MYR row or vice versa.
        val rows = buildInvestmentRows(
            accounts = listOf(
                account("MooMoo", "USD", "1000.00"),
                account("MooMoo", "MYR", "2000.00"),
            ),
            investmentTransactions = listOf(
                txn("MooMoo", "USD", "800.00"),
                txn("MooMoo", "MYR", "1900.00"),
            ),
        )
        val usd = rows.single { it.currency == "USD" }
        val myr = rows.single { it.currency == "MYR" }
        assertEquals(BigDecimal("800.00"), usd.invested)
        assertEquals(BigDecimal("200.00"), usd.unrealizedPl)
        assertEquals(BigDecimal("1900.00"), myr.invested)
        assertEquals(BigDecimal("100.00"), myr.unrealizedPl)
    }

    @Test
    fun `attribution matches bank name case-insensitively`() {
        val rows = buildInvestmentRows(
            accounts = listOf(account("myASNB", "MYR", "500.00")),
            investmentTransactions = listOf(txn("MYASNB", "MYR", "450.00")),
        )
        assertEquals(BigDecimal("450.00"), rows.single().invested)
    }
}
