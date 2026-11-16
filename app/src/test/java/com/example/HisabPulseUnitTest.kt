package com.example

import com.example.core.GstCalculator
import com.example.core.GstSupplyType
import com.example.core.LineCalculationInput
import com.example.core.Money
import com.example.core.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HisabPulseUnitTest {

    @Test
    fun `money bigdecimal conversions avoid floating point error`() {
        val price = Money.of("19.99")
        val paise = Money.toPaise(price)
        assertEquals(1999L, paise)
        assertEquals(BigDecimal("19.99"), Money.fromPaise(paise))

        val a = Money.of("0.10")
        val b = Money.of("0.20")
        assertEquals(BigDecimal("0.30"), a + b)
        assertEquals("₹••••", Money.format(Money.of("1500.00"), hideAmounts = true))
    }

    @Test
    fun `gst intra state and inter state and tax inclusive calculations`() {
        // Intra-state exclusive: 2 x 500 = 1000, 18% GST -> 90 CGST + 90 SGST = 1180
        val intra = GstCalculator.calculateLine(
            LineCalculationInput(
                quantity = BigDecimal("2"),
                unitPrice = BigDecimal("500.00"),
                taxRatePercent = BigDecimal("18")
            ),
            GstSupplyType.INTRA_STATE
        )
        assertEquals(BigDecimal("1000.00"), intra.taxableAmount)
        assertEquals(BigDecimal("90.00"), intra.cgstAmount)
        assertEquals(BigDecimal("90.00"), intra.sgstAmount)
        assertEquals(BigDecimal("0.00"), intra.igstAmount)
        assertEquals(BigDecimal("1180.00"), intra.lineTotal)

        // Inter-state exclusive: 1000 + 18% IGST = 180 IGST
        val inter = GstCalculator.calculateLine(
            LineCalculationInput(
                quantity = BigDecimal("2"),
                unitPrice = BigDecimal("500.00"),
                taxRatePercent = BigDecimal("18")
            ),
            GstSupplyType.INTER_STATE
        )
        assertEquals(BigDecimal("0.00"), inter.cgstAmount)
        assertEquals(BigDecimal("0.00"), inter.sgstAmount)
        assertEquals(BigDecimal("180.00"), inter.igstAmount)

        // Tax-inclusive: 1180 inclusive of 18% GST -> 1000 taxable + 180 tax
        val inclusive = GstCalculator.calculateLine(
            LineCalculationInput(
                quantity = BigDecimal("1"),
                unitPrice = BigDecimal("1180.00"),
                taxRatePercent = BigDecimal("18"),
                isTaxInclusive = true
            ),
            GstSupplyType.INTRA_STATE
        )
        assertEquals(BigDecimal("1000.00"), inclusive.taxableAmount)
        assertEquals(BigDecimal("180.00"), inclusive.totalTaxAmount)
        assertEquals(BigDecimal("1180.00"), inclusive.lineTotal)
    }

    @Test
    fun `document discount and roundoff calculation`() {
        val doc = GstCalculator.calculateDocument(
            lines = listOf(
                LineCalculationInput(
                    quantity = BigDecimal("1"),
                    unitPrice = BigDecimal("100.40"),
                    taxRatePercent = BigDecimal("18")
                )
            ),
            supplyType = GstSupplyType.INTRA_STATE,
            invoiceDiscountFlat = BigDecimal("0.00"),
            enableRoundOff = true
        )
        // 100.40 + 18.07 tax (18.072 -> 18.07, half = 9.04 + 9.03) = 118.47 -> rounds to 118.00 (-0.47)
        assertEquals(BigDecimal("118.00"), doc.grandTotal)
        assertEquals(BigDecimal("-0.47"), doc.roundOff)
    }

    @Test
    fun `gstin validation checks standard format`() {
        assertTrue(GstCalculator.isValidGstin(""))
        assertTrue(GstCalculator.isValidGstin("27AAPFU0939F1ZV"))
        assertFalse(GstCalculator.isValidGstin("INVALID123"))
    }

    @Test
    fun `security manager pin hashing and aes gcm encryption`() {
        val salt = SecurityManager.generateSalt()
        val hash = SecurityManager.hashPin("4826", salt)
        assertTrue(SecurityManager.verifyPin("4826", salt, hash))
        assertFalse(SecurityManager.verifyPin("0000", salt, hash))

        val payload = "{\"business\":\"HisabPulse\"}".toByteArray(Charsets.UTF_8)
        val encrypted = SecurityManager.encryptAesGcm(payload, "SecretPass#1")
        assertTrue(SecurityManager.isEncryptedBackup(encrypted))
        val decrypted = SecurityManager.decryptAesGcm(encrypted, "SecretPass#1")
        assertEquals("{\"business\":\"HisabPulse\"}", String(decrypted, Charsets.UTF_8))

        val wrongKey = runCatching { SecurityManager.decryptAesGcm(encrypted, "WrongPass") }
        assertTrue(wrongKey.isFailure)
    }
}
