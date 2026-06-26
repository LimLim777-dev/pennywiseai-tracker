package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class BoostBankParser : BankParser() {

    override fun getBankName() = "Boost Bank"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("BoostBank", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("jar")) return false
        return lower.contains("transfer of") || lower.contains("received")
    }

    override fun extractAmount(message: String): BigDecimal? {
        AMOUNT_REGEX.find(message)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("transfer of") && lower.contains("is successful") -> {
                val recipient = OUTGOING_RECIPIENT_REGEX.find(message)?.groupValues?.get(1)
                if (SelfTransferDetector.isOwnerName(recipient)) TransactionType.TRANSFER
                else TransactionType.EXPENSE
            }
            lower.contains("received") && lower.contains("into your account") -> {
                TransactionType.INCOME
            }
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        OUTGOING_RECIPIENT_REGEX.find(message)?.let { match ->
            val cleaned = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(cleaned)) return cleaned
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        INCOMING_ACCOUNT_REGEX.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    companion object {
        private val AMOUNT_REGEX = Regex(
            """(?:RM|MYR)\s*([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE
        )
        private val OUTGOING_RECIPIENT_REGEX = Regex(
            """transfer of (?:RM|MYR)\s*[0-9,.]+\s*to\s+(.+?)\s+is successful""",
            RegexOption.IGNORE_CASE
        )
        private val INCOMING_ACCOUNT_REGEX = Regex(
            """ending\s+\**(\d{4})""", RegexOption.IGNORE_CASE
        )
    }
}
