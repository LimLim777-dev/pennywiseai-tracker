package com.pennywiseai.tracker.receiver

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

enum class TNGTransactionKind { PAYMENT, RECEIVE, TRANSFER_OUT }

data class TNGParseResult(
    val amount: BigDecimal,
    val merchant: String,
    val timestamp: LocalDateTime? = null,
    val kind: TNGTransactionKind = TNGTransactionKind.PAYMENT,
)

object TNGScreenshotParser {

    // TNG detail screens always have "Wallet Ref" — used as primary fingerprint
    private val IS_TNG = Regex("""Wallet Ref""", RegexOption.IGNORE_CASE)

    // Amount at top: "-RM31.81" or "+RM20.67" or "RM2.00"
    private val AMOUNT_REGEX = Regex("""[+\-]?RM\s*([\d,]+\.\d{2})""")

    // Type detection — OCR groups all labels first, values later, so check for standalone value lines
    private val TYPE_RECEIVE  = Regex("""Receive from Wallet""", RegexOption.IGNORE_CASE)
    private val TYPE_TRANSFER = Regex("""Transfer to Wallet""", RegexOption.IGNORE_CASE)

    // Receive: name is on the line immediately after "Receive from Wallet"
    private val RECEIVE_FROM_REGEX = Regex("""Receive from Wallet\s*\n([^\n]+)""", RegexOption.IGNORE_CASE)

    // Transfer: name follows "Transfer to Wallet", skipping the optional "Transfer" nav tab line
    private val TRANSFER_TO_REGEX = Regex("""Transfer to Wallet\s*\n(?:Transfer\s*\n)?([^\n]+)""", RegexOption.IGNORE_CASE)

    // Payment merchant — "Payment - Name" (may wrap to a second line)
    private val PAYMENT_DASH_REGEX = Regex("""Payment\s*-\s*([^\n]+)(?:\n([^\n]+))?""", RegexOption.IGNORE_CASE)

    // Payment merchant fallback — name appears on the line after "+N points"
    private val POINTS_MERCHANT_REGEX = Regex("""\+\d+\s*points\s*\n([^\n]+)""", RegexOption.IGNORE_CASE)

    // Date/time appears as its own standalone line (dd/MM/yyyy HH:mm:ss)
    private val DATETIME_REGEX = Regex("""(\d{2}/\d{2}/\d{4}[\s]+\d{2}:\d{2}:\d{2})""")
    private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    suspend fun recognizeTextOnly(context: Context, imageUri: Uri): String? =
        recognizeText(context, imageUri)

    suspend fun parse(context: Context, imageUri: Uri): TNGParseResult? {
        val text = recognizeText(context, imageUri) ?: return null
        Log.d("TNGParser", "OCR text:\n---\n$text\n---")
        if (!IS_TNG.containsMatchIn(text)) {
            Log.d("TNGParser", "Not a TNG screenshot (no 'Wallet Ref')")
            return null
        }

        val amount = AMOUNT_REGEX.find(text)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()
            ?: return null

        val kind = when {
            TYPE_RECEIVE.containsMatchIn(text)  -> TNGTransactionKind.RECEIVE
            TYPE_TRANSFER.containsMatchIn(text) -> TNGTransactionKind.TRANSFER_OUT
            else                                -> TNGTransactionKind.PAYMENT
        }

        val merchant = when (kind) {
            TNGTransactionKind.RECEIVE ->
                RECEIVE_FROM_REGEX.find(text)?.groupValues?.get(1)?.trim()

            TNGTransactionKind.TRANSFER_OUT ->
                TRANSFER_TO_REGEX.find(text)?.groupValues?.get(1)?.trim()

            TNGTransactionKind.PAYMENT -> {
                val dashMatch = PAYMENT_DASH_REGEX.find(text)
                if (dashMatch != null) {
                    val line1 = dashMatch.groupValues[1].trim()
                    val line2 = dashMatch.groupValues.getOrNull(2)?.trim().orEmpty()
                    val skipLine2 = line2.isBlank() ||
                        line2.contains(Regex("""eWallet|Balance|Activity|Successful|Points|\d{8,}""", RegexOption.IGNORE_CASE))
                    if (skipLine2) line1 else "$line1 $line2"
                } else {
                    POINTS_MERCHANT_REGEX.find(text)?.groupValues?.get(1)?.trim()
                }
            }
        }?.ifBlank { null } ?: "TNG eWallet"

        val timestamp = DATETIME_REGEX.find(text)
            ?.groupValues?.get(1)
            ?.let { runCatching { LocalDateTime.parse(it.trim(), DATETIME_FORMAT) }.getOrNull() }

        return TNGParseResult(amount = amount, merchant = merchant, timestamp = timestamp, kind = kind)
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
