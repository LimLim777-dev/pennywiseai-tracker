package com.pennywiseai.tracker.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Regression suite for [TNGScreenshotParser.parseText] (review M-R2).
 *
 * Fixtures are transcriptions of REAL ML Kit OCR dumps collected via the
 * in-app "TNG OCR Debug" tool on 2026-07-06, with person names masked
 * (PII rule). The structural quirks are the point: ML Kit reads TNG's
 * label/value two-column layout as ALL labels first, values later, and the
 * bottom-nav "Transfer" tab can appear between "Transfer to Wallet" and the
 * counterparty name.
 */
class TNGScreenshotParserTest {

    @Test
    fun `receive from wallet - name on line after marker`() {
        val ocr = """
            MY MAXIS 5:25 O
            < Details
            +RM16.96
            Transaction Type
            Receive From
            Payment Details
            Payment Method
            Date/Time
            Wallet Ref
            Status
            Transaction No.
            Home
            Transfer
            43%
            Receive from Wallet
            PERSON ONE
            Fund Transfer
            eWallet Balance
            20260704111217000103001715
            04/07/2026 13:19:46
            228974344
            Activity
            Successful
        """.trimIndent()

        val result = TNGScreenshotParser.parseText(ocr)
        assertNotNull(result)
        assertEquals(TNGTransactionKind.RECEIVE, result!!.kind)
        assertEquals(BigDecimal("16.96"), result.amount)
        assertEquals("PERSON ONE", result.merchant)
        assertEquals(LocalDateTime.of(2026, 7, 4, 13, 19, 46), result.timestamp)
    }

    @Test
    fun `transfer to wallet - skips the bottom-nav Transfer line`() {
        val ocr = """
            MY MAXIS 5:54
            < Details
            -RM16.96
            Transaction Type
            Transfer To
            Payment Details
            Payment Method
            Date/Time
            Wallet Ref
            Status
            Transaction No.
            Home
            9VoWii 40%
            Transfer to Wallet
            Transfer
            PERSON ONE
            Fund Transfer
            eWallet Balance
            20260704111217000101001719
            04/07/2026 10:26:42
            265933966
            Add To Favourites
            Successful
        """.trimIndent()

        val result = TNGScreenshotParser.parseText(ocr)
        assertNotNull(result)
        assertEquals(TNGTransactionKind.TRANSFER_OUT, result!!.kind)
        assertEquals(BigDecimal("16.96"), result.amount)
        assertEquals("PERSON ONE", result.merchant)
        assertEquals(LocalDateTime.of(2026, 7, 4, 10, 26, 42), result.timestamp)
    }

    @Test
    fun `payment - merchant from two-line Payment dash form`() {
        val ocr = """
            MY MAXIS 4:45S
            ( Details
            -RM2.00
            Transaction Type
            Merchant
            Payment Details
            Payment Method
            Date/Time
            Wallet Ref
            Status
            Home
            7-ELEVEN MALAYSIA SDN BHD
            Transaction No.
            9oWiF .|47%
            +4 points
            Transfer
            Payment
            Payment - 7-ELEVEN
            MALAYSIA SDN BHD
            eWallet Balance
            2026062510110000010000TNGO
            25/06/2026 14:44:41
            w3MY171985207785814
            Merchants can scan the code for
            refund or query transaction
        """.trimIndent()

        val result = TNGScreenshotParser.parseText(ocr)
        assertNotNull(result)
        assertEquals(TNGTransactionKind.PAYMENT, result!!.kind)
        assertEquals(BigDecimal("2.00"), result.amount)
        assertEquals("7-ELEVEN MALAYSIA SDN BHD", result.merchant)
        assertEquals(LocalDateTime.of(2026, 6, 25, 14, 44, 41), result.timestamp)
    }

    @Test
    fun `payment - merchant falls back to line after points`() {
        val ocr = """
            MY MAXIS 4:45S
            < Details
            -RM31.81
            Transaction Type
            Merchant
            Payment Method
            Date/Time
            Wallet Ref
            Payment Details
            GWPO02606230020800689
            Status
            Transaction No.
            Home
            47%
            Transfer
            +31 points
            PINDUODUO
            Payment
            2084225897
            eWallet Balance
            2026062310110000010000TNGO
            23/06/2026 00:49:23
            Powered hy klipoay+
            W3MY171985206842619
            Activity
        """.trimIndent()

        val result = TNGScreenshotParser.parseText(ocr)
        assertNotNull(result)
        assertEquals(TNGTransactionKind.PAYMENT, result!!.kind)
        assertEquals(BigDecimal("31.81"), result.amount)
        assertEquals("PINDUODUO", result.merchant)
        assertEquals(LocalDateTime.of(2026, 6, 23, 0, 49, 23), result.timestamp)
    }

    @Test
    fun `non-TNG text returns null`() {
        assertNull(TNGScreenshotParser.parseText("Some random screenshot text without the fingerprint"))
    }
}
