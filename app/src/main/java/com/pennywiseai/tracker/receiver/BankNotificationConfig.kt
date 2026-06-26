package com.pennywiseai.tracker.receiver

import android.app.Notification

object BankNotificationConfig {

    private val allowedPackages: Map<String, String> = mapOf(
        // Faysal Bank (Pakistan) – alias must match FaysalBankParser.canHandle()
        "com.avanza.ambitwizfbl" to "FaysalBank",
        // Enpara (Turkey) – alias must match EnparaBankParser.canHandle()
        "finansbank.enpara" to "Enpara",
        "com.enparabank.retail" to "Enpara",
        // Malaysia — alias must match each parser's canHandle()
        "co.myboostbank.boostberhad" to "BoostBank",
        "com.maybank2u.life" to "Maybank2u",
        "my.com.gxbank.app" to "GXBank",
        "com.pbb.mypb" to "PublicBank",
        "my.com.tngdigital.ewallet" to "TNG",
        "my.rytbank.app" to "RytBank"
    )

    fun isAllowed(packageName: String): Boolean =
        allowedPackages.containsKey(packageName.lowercase())

    fun senderAlias(packageName: String): String =
        allowedPackages[packageName.lowercase()] ?: packageName

    fun extractMessage(notification: Notification): String {
        val extras = notification.extras ?: return ""

        val textParts = buildList {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it) }
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { add(it) }
        }

        if (textParts.isNotEmpty()) {
            val merged = textParts.joinToString("\n") { it.toString() }.trim()
            if (merged.isNotBlank()) return merged
        }

        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    }
}
