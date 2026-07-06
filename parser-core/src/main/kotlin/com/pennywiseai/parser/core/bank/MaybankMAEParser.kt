package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class MaybankMAEParser : BankParser() {

    override fun getBankName() = "Maybank2u"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("Maybank2u", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("transferred") ||
            lower.contains("duitnow autodebit") ||
            (lower.contains("has charged") && lower.contains("duitnow")) ||
            (lower.contains("successful payment of") && lower.contains("fpx"))
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
            lower.contains("transferred") -> {
                val recipient = RECIPIENT_REGEX.find(message)?.groupValues?.get(1)
                if (SelfTransferDetector.isOwnerName(recipient)) TransactionType.TRANSFER
                else TransactionType.EXPENSE
            }
            lower.contains("duitnow autodebit") || (lower.contains("has charged") && lower.contains("duitnow")) ->
                TransactionType.EXPENSE
            lower.contains("successful payment of") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        return when {
            lower.contains("transferred") -> RECIPIENT_REGEX.find(message)
                ?.groupValues?.get(1)?.trim()?.let { cleanMerchantName(it) }
                ?.takeIf { isValidMerchantName(it) }
            lower.contains("duitnow autodebit") || (lower.contains("has charged") && lower.contains("duitnow")) ->
                DUITNOW_MERCHANT_REGEX.find(message)?.groupValues?.get(1)?.trim()
                    ?.let { cleanMerchantName(it) }?.takeIf { isValidMerchantName(it) }
            lower.contains("successful payment of") -> FPX_MERCHANT_REGEX.find(message)
                ?.groupValues?.get(1)?.trim()?.let { cleanMerchantName(it) }
                ?.takeIf { isValidMerchantName(it) }
            else -> null
        }
    }

    override fun extractAccountLast4(message: String): String? =
        MASKED_ACCOUNT_REGEX.find(message)?.groupValues?.get(1)

    override fun extractReference(message: String): String? {
        REF_REGEX.find(message)?.let { return it.groupValues[1] }
        FPX_ID_REGEX.find(message)?.let { return it.groupValues[1] }
        return null
    }

    companion object {
        private val AMOUNT_REGEX = Regex(
            """(?:RM|MYR)\s*([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE
        )
        private val RECIPIENT_REGEX = Regex(
            """transferred\s+(?:RM|MYR)\s*[0-9,.]+\s*to\s+(.+?)'s\s""",
            RegexOption.IGNORE_CASE
        )
        private val FPX_MERCHANT_REGEX = Regex(
            """payment of\s+(?:RM|MYR)\s*[0-9,.]+\s+to\s+(.+?)\s+on\s""",
            RegexOption.IGNORE_CASE
        )
        private val REF_REGEX = Regex("""REF:?\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        private val FPX_ID_REGEX = Regex("""FPX ID:?\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        private val DUITNOW_MERCHANT_REGEX = Regex("""^(.+?)\s+has charged""", RegexOption.IGNORE_CASE)
        private val MASKED_ACCOUNT_REGEX = Regex("""\*{2,3}(\d{4})""")
    }
}
