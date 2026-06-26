package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class UOBCardParser : BankParser() {

    override fun getBankName() = "UOB"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.trim() in KNOWN_SENDERS

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("one-time password")) return false
        return lower.contains("uob card") && lower.contains("for myr")
    }

    override fun extractAmount(message: String): BigDecimal? {
        MAIN_REGEX.find(message)?.let {
            return it.groupValues[3].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? =
        if (isTransactionMessage(message)) TransactionType.EXPENSE else null

    override fun extractMerchant(message: String, sender: String): String? {
        MAIN_REGEX.find(message)?.let { match ->
            val cleaned = cleanMerchantName(match.groupValues[2].trim())
            if (isValidMerchantName(cleaned)) return cleaned
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        MAIN_REGEX.find(message)?.let { return it.groupValues[1] }
        return null
    }

    companion object {
        private val KNOWN_SENDERS = setOf("67425", "66300")
        private val MAIN_REGEX = Regex(
            """ending\s+(\d{4})\s*@?\s*(.+?)\s+for\s+MYR\s*([0-9,]+\.\d{2})""",
            RegexOption.IGNORE_CASE
        )
    }
}
