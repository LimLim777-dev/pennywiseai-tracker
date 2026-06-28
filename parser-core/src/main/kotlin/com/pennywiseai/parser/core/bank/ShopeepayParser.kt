package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class ShopeepayParser : BankParser() {

    override fun getBankName() = "ShopeePay"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("ShopeePay", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        // Only capture Money+ auto top-up notifications — these fire when ShopeePay
        // automatically draws from Money+ savings to cover a checkout payment.
        // Ignore manual top-up confirmations to avoid double-counting.
        return lower.contains("money+") && lower.contains("auto top up to wallet")
    }

    override fun extractAmount(message: String): BigDecimal? {
        AMOUNT_REGEX.find(message)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType =
        TransactionType.EXPENSE

    override fun extractMerchant(message: String, sender: String): String? = "ShopeePay"

    companion object {
        private val AMOUNT_REGEX = Regex(
            """(?:RM|MYR)\s*([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE
        )
    }
}
