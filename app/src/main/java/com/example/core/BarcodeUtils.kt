package com.example.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.math.BigDecimal

object BarcodeUtils {
    fun generateCode128Bitmap(
        content: String,
        width: Int = 600,
        height: Int = 180
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val bitMatrix = MultiFormatWriter().encode(
                content.trim(),
                BarcodeFormat.CODE_128,
                width,
                height
            )
            matrixToBitmap(bitMatrix)
        } catch (_: Exception) {
            null
        }
    }

    fun generateQrBitmap(
        content: String,
        size: Int = 420
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size
            )
            matrixToBitmap(bitMatrix)
        } catch (_: Exception) {
            null
        }
    }

    fun buildUpiQrPayload(
        upiId: String,
        payeeName: String,
        amount: BigDecimal,
        note: String
    ): String {
        val cleanUpi = upiId.trim()
        val cleanName = payeeName.trim().replace(" ", "%20")
        val cleanNote = note.trim().replace(" ", "%20")
        val amt = Money.formatPlain(amount)
        return "upi://pay?pa=$cleanUpi&pn=$cleanName&am=$amt&cu=INR&tn=$cleanNote"
    }

    private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
