package com.example.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Decimal-safe monetary helper storing minor units (paise/cents) in Long
 * and performing all arithmetic with BigDecimal (HALF_UP, scale 2).
 */
object Money {
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal("100")

    fun of(value: String?): BigDecimal {
        if (value.isNullOrBlank()) return ZERO
        return try {
            BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP)
        } catch (_: NumberFormatException) {
            ZERO
        }
    }

    fun of(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    fun fromPaise(paise: Long): BigDecimal =
        BigDecimal.valueOf(paise, 2).setScale(2, RoundingMode.HALF_UP)

    fun toPaise(amount: BigDecimal): Long =
        amount.setScale(2, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .longValueExact()

    fun safeToPaise(amount: BigDecimal): Long =
        try {
            toPaise(amount)
        } catch (_: ArithmeticException) {
            amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .toLong()
        }

    fun percentageOf(base: BigDecimal, percent: BigDecimal): BigDecimal {
        if (base.compareTo(BigDecimal.ZERO) == 0 || percent.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO
        }
        return base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP)
    }

    fun format(
        amount: BigDecimal,
        currencySymbol: String = "₹",
        hideAmounts: Boolean = false
    ): String {
        if (hideAmounts) return "$currencySymbol••••"
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        val isNegative = scaled < BigDecimal.ZERO
        val absVal = scaled.abs()
        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val formatted = formatter.format(absVal)
        return if (isNegative) "-$currencySymbol$formatted" else "$currencySymbol$formatted"
    }

    fun formatPaise(
        paise: Long,
        currencySymbol: String = "₹",
        hideAmounts: Boolean = false
    ): String = format(fromPaise(paise), currencySymbol, hideAmounts)

    fun formatPlain(amount: BigDecimal): String =
        amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

    fun formatQty(qty: BigDecimal): String {
        val stripped = qty.stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toPlainString() else stripped.toPlainString()
    }
}
