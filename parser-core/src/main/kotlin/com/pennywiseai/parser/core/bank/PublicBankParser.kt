package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class PublicBankParser : BankParser() {

    override fun getBankName() = "Public Bank"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("PublicBank", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("duitnow transfer") ||
            (lower.contains("fpx payment") && lower.contains("you have paid"))
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
            lower.contains("received") -> FROM_REGEX.find(message)?.groupValues?.get(1)
            lower.contains("sent") -> TO_REGEX.find(message)?.groupValues?.get(1)
            else -> null
        }
        if (SelfTransferDetector.isOwnerName(counterparty)) return TransactionType.TRANSFER
        return when {
            lower.contains("you have paid") -> TransactionType.EXPENSE
            lower.contains("received") -> TransactionType.INCOME
            lower.contains("sent") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        val match = when {
            lower.contains("you have paid") -> FPX_MERCHANT_REGEX.find(message)
            lower.contains("received") -> FROM_REGEX.find(message)
            else -> TO_REGEX.find(message)
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
        private val FROM_REGEX = Regex("""from\s+(.+?)\.?$""", RegexOption.IGNORE_CASE)
        private val TO_REGEX = Regex("""to\s+(.+?)\.?$""", RegexOption.IGNORE_CASE)
        private val FPX_MERCHANT_REGEX = Regex(
            """paid\s+(?:RM|MYR)\s*[0-9,.]+\s+to\s+(.+?)\s+via""", RegexOption.IGNORE_CASE
        )
    }
}
