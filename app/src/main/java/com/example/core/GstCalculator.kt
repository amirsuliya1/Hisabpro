package com.example.core

import java.math.BigDecimal
import java.math.RoundingMode

enum class GstSupplyType {
    INTRA_STATE,
    INTER_STATE,
    NON_GST
}

data class LineCalculationInput(
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val taxRatePercent: BigDecimal = BigDecimal.ZERO,
    val isTaxInclusive: Boolean = false
)

data class LineCalculationResult(
    val grossAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val taxableAmount: BigDecimal,
    val cgstAmount: BigDecimal,
    val sgstAmount: BigDecimal,
    val igstAmount: BigDecimal,
    val totalTaxAmount: BigDecimal,
    val lineTotal: BigDecimal
)

data class DocumentCalculationResult(
    val subtotal: BigDecimal,
    val itemDiscountTotal: BigDecimal,
    val invoiceDiscountAmount: BigDecimal,
    val taxableAmount: BigDecimal,
    val cgstTotal: BigDecimal,
    val sgstTotal: BigDecimal,
    val igstTotal: BigDecimal,
    val totalTax: BigDecimal,
    val additionalCharges: BigDecimal,
    val shippingCharges: BigDecimal,
    val roundOff: BigDecimal,
    val grandTotal: BigDecimal
)

object GstCalculator {
    private val HUNDRED = BigDecimal("100")
    private val TWO = BigDecimal("2")

    fun calculateLine(
        input: LineCalculationInput,
        supplyType: GstSupplyType
    ): LineCalculationResult {
        val qty = input.quantity.max(BigDecimal.ZERO)
        val price = input.unitPrice.max(BigDecimal.ZERO)
        val rawGross = qty.multiply(price).setScale(2, RoundingMode.HALF_UP)

        val percentDisc = Money.percentageOf(rawGross, input.discountPercent.max(BigDecimal.ZERO))
        val effectiveDisc = (percentDisc + input.discountAmount.max(BigDecimal.ZERO))
            .min(rawGross)
            .setScale(2, RoundingMode.HALF_UP)

        val discountedAmount = (rawGross - effectiveDisc).setScale(2, RoundingMode.HALF_UP)
        val effectiveRate = if (supplyType == GstSupplyType.NON_GST) {
            BigDecimal.ZERO
        } else {
            input.taxRatePercent.max(BigDecimal.ZERO)
        }

        val taxable: BigDecimal
        val totalTax: BigDecimal
        val lineTotal: BigDecimal

        if (effectiveRate.compareTo(BigDecimal.ZERO) == 0) {
            taxable = discountedAmount
            totalTax = Money.ZERO
            lineTotal = discountedAmount
        } else if (input.isTaxInclusive) {
            val divisor = HUNDRED.add(effectiveRate)
            taxable = discountedAmount.multiply(HUNDRED)
                .divide(divisor, 2, RoundingMode.HALF_UP)
            totalTax = (discountedAmount - taxable).setScale(2, RoundingMode.HALF_UP)
            lineTotal = discountedAmount
        } else {
            taxable = discountedAmount
            totalTax = taxable.multiply(effectiveRate)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
            lineTotal = (taxable + totalTax).setScale(2, RoundingMode.HALF_UP)
        }

        val cgst: BigDecimal
        val sgst: BigDecimal
        val igst: BigDecimal
        when (supplyType) {
            GstSupplyType.INTRA_STATE -> {
                cgst = totalTax.divide(TWO, 2, RoundingMode.HALF_UP)
                sgst = (totalTax - cgst).setScale(2, RoundingMode.HALF_UP)
                igst = Money.ZERO
            }
            GstSupplyType.INTER_STATE -> {
                cgst = Money.ZERO
                sgst = Money.ZERO
                igst = totalTax
            }
            GstSupplyType.NON_GST -> {
                cgst = Money.ZERO
                sgst = Money.ZERO
                igst = Money.ZERO
            }
        }

        return LineCalculationResult(
            grossAmount = rawGross,
            discountAmount = effectiveDisc,
            taxableAmount = taxable,
            cgstAmount = cgst,
            sgstAmount = sgst,
            igstAmount = igst,
            totalTaxAmount = totalTax,
            lineTotal = lineTotal
        )
    }

    fun calculateDocument(
        lines: List<LineCalculationInput>,
        supplyType: GstSupplyType,
        invoiceDiscountPercent: BigDecimal = BigDecimal.ZERO,
        invoiceDiscountFlat: BigDecimal = BigDecimal.ZERO,
        additionalCharges: BigDecimal = BigDecimal.ZERO,
        shippingCharges: BigDecimal = BigDecimal.ZERO,
        enableRoundOff: Boolean = true
    ): DocumentCalculationResult {
        val computedLines = lines.map { calculateLine(it, supplyType) }
        val subtotal = computedLines.fold(Money.ZERO) { acc, r -> acc + r.grossAmount }
        val itemDiscountTotal = computedLines.fold(Money.ZERO) { acc, r -> acc + r.discountAmount }
        val preInvoiceDiscTaxable = computedLines.fold(Money.ZERO) { acc, r -> acc + r.taxableAmount }
        val preInvoiceDiscCgst = computedLines.fold(Money.ZERO) { acc, r -> acc + r.cgstAmount }
        val preInvoiceDiscSgst = computedLines.fold(Money.ZERO) { acc, r -> acc + r.sgstAmount }
        val preInvoiceDiscIgst = computedLines.fold(Money.ZERO) { acc, r -> acc + r.igstAmount }

        val lineSumBeforeInvDiscount = (preInvoiceDiscTaxable + preInvoiceDiscCgst + preInvoiceDiscSgst + preInvoiceDiscIgst)
            .setScale(2, RoundingMode.HALF_UP)

        val percentInvDisc = Money.percentageOf(preInvoiceDiscTaxable, invoiceDiscountPercent.max(BigDecimal.ZERO))
        val requestedInvDisc = (percentInvDisc + invoiceDiscountFlat.max(BigDecimal.ZERO))
            .min(preInvoiceDiscTaxable)
            .setScale(2, RoundingMode.HALF_UP)

        // Pro-rate tax reduction when an invoice-level discount is applied
        val discountFactor = if (preInvoiceDiscTaxable > BigDecimal.ZERO && requestedInvDisc > BigDecimal.ZERO) {
            BigDecimal.ONE - requestedInvDisc.divide(preInvoiceDiscTaxable, 6, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ONE
        }

        val taxableAmount = (preInvoiceDiscTaxable - requestedInvDisc).setScale(2, RoundingMode.HALF_UP)
        val cgstTotal = preInvoiceDiscCgst.multiply(discountFactor).setScale(2, RoundingMode.HALF_UP)
        val sgstTotal = preInvoiceDiscSgst.multiply(discountFactor).setScale(2, RoundingMode.HALF_UP)
        val igstTotal = preInvoiceDiscIgst.multiply(discountFactor).setScale(2, RoundingMode.HALF_UP)
        val totalTax = (cgstTotal + sgstTotal + igstTotal).setScale(2, RoundingMode.HALF_UP)

        val addCharges = additionalCharges.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val shipCharges = shippingCharges.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        val rawTotal = (taxableAmount + totalTax + addCharges + shipCharges)
            .min(lineSumBeforeInvDiscount + addCharges + shipCharges)
            .setScale(2, RoundingMode.HALF_UP)

        val finalTotal: BigDecimal
        val roundOff: BigDecimal
        if (enableRoundOff) {
            finalTotal = rawTotal.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
            roundOff = (finalTotal - rawTotal).setScale(2, RoundingMode.HALF_UP)
        } else {
            finalTotal = rawTotal
            roundOff = Money.ZERO
        }

        return DocumentCalculationResult(
            subtotal = subtotal,
            itemDiscountTotal = itemDiscountTotal,
            invoiceDiscountAmount = requestedInvDisc,
            taxableAmount = taxableAmount,
            cgstTotal = cgstTotal,
            sgstTotal = sgstTotal,
            igstTotal = igstTotal,
            totalTax = totalTax,
            additionalCharges = addCharges,
            shippingCharges = shipCharges,
            roundOff = roundOff,
            grandTotal = finalTotal
        )
    }

    fun isValidGstin(gstin: String): Boolean {
        if (gstin.isBlank()) return true
        val regex = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")
        return regex.matches(gstin.trim().uppercase())
    }
}
