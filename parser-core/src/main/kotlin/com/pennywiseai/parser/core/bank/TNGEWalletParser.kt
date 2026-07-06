package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class TNGEWalletParser : BankParser() {

    override fun getBankName() = "TNG eWallet"
    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.equals("TNG", ignoreCase = true)

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        val hasTransaction = lower.contains("transferred to") ||
            lower.contains("received from") ||
            lower.contains("successfully transferred") ||
            (lower.contains("has transferred") && lower.contains("to you")) ||
            lower.contains("has been deducted from your tng ewallet") ||
            (lower.contains("received") && lower.contains("cashback"))
        // Skip GO+ cash-in notifications only when there's no peer transfer content —
        // stacked notifications can merge GO+ cash-in with a "received from" line.
        if (lower.contains("go+") && !hasTransaction) return false
        return hasTransaction
    }

    override fun extractAmount(message: String): BigDecimal? {
        AMOUNT_REGEX.find(message)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        if (lower.contains("has been deducted from your tng ewallet")) return TransactionType.EXPENSE
        if (lower.contains("received") && lower.contains("cashback")) return TransactionType.INCOME
        val counterparty = extractCounterparty(message)
        if (SelfTransferDetector.isOwnerName(counterparty)) return TransactionType.TRANSFER
        return when {
            lower.contains("successfully transferred") -> TransactionType.EXPENSE
            lower.contains("transferred to") -> TransactionType.EXPENSE
            lower.contains("received from") -> TransactionType.INCOME
            lower.contains("has transferred") && lower.contains("to you") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        // Merchant QR payment: "MERCHANT NAME: RM94.65 has been deducted from your TNG eWallet"
        if (lower.contains("has been deducted from your tng ewallet")) {
            val match = MERCHANT_DEDUCTED_REGEX.find(message)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val cleaned = cleanMerchantName(name)
                return cleaned.takeIf { isValidMerchantName(it) }
            }
        }
        // Cashback — TNG is the source
        if (lower.contains("cashback")) return "TNG eWallet"
        val counterparty = extractCounterparty(message) ?: return null
        val cleaned = cleanMerchantName(counterparty.trim())
        return cleaned.takeIf { isValidMerchantName(it) }
    }

    override fun extractReference(message: String): String? {
        MERCHANT_REF_REGEX.find(message)?.let { return it.groupValues[1] }
        return super.extractReference(message)
    }

    private fun extractCounterparty(message: String): String? {
        val lower = message.lowercase()
        val match = when {
            lower.contains("successfully transferred") ->
                SUCCESSFUL_TRANSFER_REGEX.find(message) ?: TRANSFER_OUT_REGEX.find(message)
            lower.contains("transferred to") -> TRANSFER_OUT_REGEX.find(message)
            lower.contains("received from") -> RECEIVED_REGEX.find(message)
            else -> HAS_TRANSFERRED_REGEX.find(message)
        }
        return match?.groupValues?.get(1)
    }

    companion object {
        private val AMOUNT_REGEX = Regex(
            """(?:RM|MYR)\s*([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE
        )
        private val TRANSFER_OUT_REGEX = Regex(
            """transferred to\s+(.+?)\.""", RegexOption.IGNORE_CASE
        )
        private val SUCCESSFUL_TRANSFER_REGEX = Regex(
            """successfully transferred\s+(?:RM|MYR)\s*[0-9,.]+\s+to\s+(.+?)\.""",
            RegexOption.IGNORE_CASE
        )
        private val RECEIVED_REGEX = Regex(
            """received from\s+(.+?)(?:\s+for\s|\.)""", RegexOption.IGNORE_CASE
        )
        private val HAS_TRANSFERRED_REGEX = Regex(
            """^(.+?)\s+has transferred\s+(?:RM|MYR)""", RegexOption.IGNORE_CASE
        )
        // "PAULINE'S KRYSTAL POIN: RM94.65 has been deducted from your TNG eWallet"
        private val MERCHANT_DEDUCTED_REGEX = Regex(
            """^(.+?):\s*(?:RM|MYR)\s*[0-9,]+\.\d{2}\s+has been deducted""",
            RegexOption.IGNORE_CASE
        )
        // "Merchant Reference No. DM10P5L3Q2UNC"
        private val MERCHANT_REF_REGEX = Regex(
            """Merchant Reference No\.\s*(\S+)""", RegexOption.IGNORE_CASE
        )
    }
}
