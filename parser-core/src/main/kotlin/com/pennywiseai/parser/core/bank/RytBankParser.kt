package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class RytBankParser : BankParser() {

    override fun getBankName() = "Ryt Bank"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("RytBank", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("you've paid") || lower.contains("you've received")
    }

    override fun extractAmount(message: String): BigDecimal? {
        AMOUNT_REGEX.find(message)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        val counterparty = if (lower.contains("you've paid")) {
            PAID_REGEX.find(message)?.groupValues?.get(1)
        } else {
            RECEIVED_REGEX.find(message)?.groupValues?.get(1)
        }
        if (SelfTransferDetector.isOwnerName(counterparty)) return TransactionType.TRANSFER
        return when {
            lower.contains("you've paid") -> TransactionType.EXPENSE
            lower.contains("you've received") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        val match = if (lower.contains("you've paid")) PAID_REGEX.find(message) else RECEIVED_REGEX.find(message)
        match?.let {
            val cleaned = cleanMerchantName(it.groupValues[1].trim())
            if (isValidMerchantName(cleaned)) return cleaned
        }
        return null
    }

    companion object {
        private val AMOUNT_REGEX = Regex(
            """(?:RM|MYR)\s*([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE
        )
        private val PAID_REGEX = Regex(
            """paid\s+(?:RM|MYR)\s*[0-9,.]+\s+to\s+(.+?)\s+on\s""", RegexOption.IGNORE_CASE
        )
        private val RECEIVED_REGEX = Regex(
            """received\s+(?:RM|MYR)\s*[0-9,.]+\s+from\s+(.+?)\s+on\s""", RegexOption.IGNORE_CASE
        )
    }
}
