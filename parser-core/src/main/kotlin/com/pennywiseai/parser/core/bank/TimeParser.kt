package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Time Internet (Malaysia) payment receipts (SMS shortcode 62003).
 *
 * The Time fibre bill is auto-charged to the UOB ONE credit card, but UOB
 * sends no SMS for recurring auto-debits — Time's own receipt is the only
 * signal, and the billed amount varies month to month (so it can't be a
 * fixed scheduled entry). Bank name is deliberately "UOB": the money leaves
 * the card, and recording it there keeps the UOB cashback tracker's
 * minimum-spend count honest (statement-verified: a TIMEDOTCOM retail line
 * posts to the card each month, 2 days after this receipt arrives).
 *
 * Sample (service account number masked):
 * "RM0 Time:
 *  Thanks! We've received your payment.
 *  A/C No: 123456789012
 *  Amt: RM102.80
 *  Date: 21 May 2026
 *  Download the Time Fibre Home app to check your updated balance. TQ."
 */
class TimeParser : BankParser() {

    override fun getBankName() = "UOB"

    override fun getCurrency() = "MYR"

    override fun canHandle(sender: String): Boolean =
        sender.trim().uppercase() in KNOWN_SENDERS

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("received your payment") && lower.contains("amt:")
    }

    override fun extractAmount(message: String): BigDecimal? =
        AMOUNT_REGEX.find(message)?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()

    override fun extractTransactionType(message: String): TransactionType? =
        if (isTransactionMessage(message)) TransactionType.EXPENSE else null

    override fun extractMerchant(message: String, sender: String): String = "Time Fibre"

    /**
     * The receipt's "A/C No" is the 12-digit Time service account, not a
     * card — the base extractor would otherwise mint a bogus UOB account
     * from its last four digits.
     */
    override fun extractAccountLast4(message: String): String? = null

    companion object {
        private val KNOWN_SENDERS = setOf("62003", "TIME")
        private val AMOUNT_REGEX = Regex(
            """Amt:\s*RM\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
    }
}
