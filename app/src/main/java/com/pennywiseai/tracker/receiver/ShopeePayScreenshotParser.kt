package com.pennywiseai.tracker.receiver

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

data class ShopeePayParseResult(
    val amount: BigDecimal,
    val merchant: String,
    val timestamp: LocalDateTime? = null,
)

object ShopeePayScreenshotParser {

    // The top header always shows "-RM8.83" — most reliable since ML Kit reads it first
    private val AMOUNT_REGEX_HEADER = Regex("""-RM\s*([\d,]+\.\d{2})""")
    // Fallback: "You Paid" then find RM amount within generous window (handles row-by-row OCR)
    private val AMOUNT_REGEX_YOU_PAID = Regex("""You Paid[\s\S]{0,300}?RM\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    // Last resort: any RM amount in the document
    private val AMOUNT_REGEX_ANY = Regex("""RM\s*([\d,]+\.\d{2})""")

    // Lines to skip when searching for merchant name after "Pay To"
    private val SKIP_LINE_PATTERNS = listOf(
        Regex("""-?RM\s*[\d,]+\.\d{2}.*""", RegexOption.IGNORE_CASE), // amounts like -RM11.50
        Regex("""Successful""", RegexOption.IGNORE_CASE),
        Regex("""Completed Time.*""", RegexOption.IGNORE_CASE),
        Regex("""View Original Receipt""", RegexOption.IGNORE_CASE),
        Regex("""You Paid.*""", RegexOption.IGNORE_CASE),
        Regex("""Purchase Amount.*""", RegexOption.IGNORE_CASE),
        Regex("""Redeem.*""", RegexOption.IGNORE_CASE),
        Regex("""Rewards""", RegexOption.IGNORE_CASE),
        Regex("""Order Details""", RegexOption.IGNORE_CASE),
    )

    // "Completed Time 29-06-2026 13:23"
    private val TIMESTAMP_REGEX = Regex("""Completed Time\s+(\d{2}-\d{2}-\d{4}\s+\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

    suspend fun parse(context: Context, imageUri: Uri): ShopeePayParseResult? {
        val text = recognizeText(context, imageUri) ?: return null

        // Must contain all three ShopeePay payment confirmation markers
        val lower = text.lowercase()
        if (!lower.contains("you paid") || !lower.contains("pay to") || !lower.contains("successful")) return null

        // Try header amount first ("-RM8.83"), then "You Paid" region, then any RM amount
        val amount = (AMOUNT_REGEX_HEADER.find(text)
            ?: AMOUNT_REGEX_YOU_PAID.find(text)
            ?: AMOUNT_REGEX_ANY.find(text))
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()
            ?: return null

        val merchant = extractMerchant(text) ?: "ShopeePay"

        val timestamp = TIMESTAMP_REGEX.find(text)
            ?.groupValues?.get(1)
            ?.let { runCatching { LocalDateTime.parse(it.trim(), TIMESTAMP_FORMAT) }.getOrNull() }

        return ShopeePayParseResult(amount = amount, merchant = merchant, timestamp = timestamp)
    }

    private fun extractMerchant(text: String): String? {
        val idx = text.indexOf("Pay To", ignoreCase = true)
        if (idx < 0) return null
        val afterPayTo = text.substring(idx + 6)
        for (line in afterPayTo.split('\n', '\r').map { it.trim() }.filter { it.isNotBlank() }) {
            if (SKIP_LINE_PATTERNS.any { it.matches(line) }) continue
            return line
        }
        return null
    }

    private suspend fun recognizeText(context: Context, imageUri: Uri): String? =
        suspendCancellableCoroutine { cont ->
            try {
                val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: run { cont.resume(null); return@suspendCancellableCoroutine }

                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { result -> cont.resume(result.text) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }
}
