package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class GXBankParser : BankParser() {

    override fun getBankName() = "GXBank"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("GXBank", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return (lower.contains("transaction") && lower.contains("is successful")) ||
            lower.contains("you've received")
    }

    override fun extractAmount(message: String): BigDecimal? {
        AMOUNT_REGEX.find(message)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        val counterparty = when {
            lower.contains("you've received") -> RECEIVED_REGEX.find(message)?.groupValues?.get(1)
            else -> TO_REGEX.find(message)?.groupValues?.get(1)
        }
        if (SelfTransferDetector.isOwnerName(counterparty)) return TransactionType.TRANSFER
        return when {
            lower.contains("you've received") -> TransactionType.INCOME
            TO_REGEX.containsMatchIn(message) -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val match = if (message.lowercase().contains("you've received")) {
            RECEIVED_REGEX.find(message)
        } else {
            TO_REGEX.find(message)
        }
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
        private val TO_REGEX = Regex(
            """transaction\s+(?:RM|MYR)\s*[0-9,.]+\s+to\s+(.+?)\s+is successful""",
            RegexOption.IGNORE_CASE
        )
        private val RECEIVED_REGEX = Regex(
            """received\s+(?:RM|MYR)\s*[0-9,.]+\s+from\s+(.+?)\.""",
            RegexOption.IGNORE_CASE
        )
    }
}
