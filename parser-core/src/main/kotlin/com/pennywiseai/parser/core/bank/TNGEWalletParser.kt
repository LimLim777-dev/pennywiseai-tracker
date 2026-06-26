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
        if (lower.contains("go+")) return false
        return lower.contains("transferred to") ||
            lower.contains("received from") ||
            lower.contains("successfully transferred") ||
            (lower.contains("has transferred") && lower.contains("to you"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        AMOUNT_REGEX.find(message)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
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
        val counterparty = extractCounterparty(message) ?: return null
        val cleaned = cleanMerchantName(counterparty.trim())
        return cleaned.takeIf { isValidMerchantName(it) }
    }

    private fun extractCounterparty(message: String): String? {
        val lower = message.lowercase()
        val match = when {
            lower.contains("successfully transferred") -> SUCCESSFUL_TRANSFER_REGEX.find(message)
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
    }
}
