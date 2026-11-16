package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.DailyClosing
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoicePaperSize
import com.example.data.local.entities.InvoiceTemplate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfGenerator {

    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun parseHexColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Throwable) {
            fallback
        }
    }

    fun generateInvoicePdf(
        context: Context,
        profile: BusinessProfile?,
        invoice: Invoice,
        items: List<InvoiceItem>,
        template: InvoiceTemplate?
    ): File {
        val code = template?.code ?: "CORPORATE_WAVE"
        val outFile = File(exportsDir(context), "${invoice.invoiceNumber.replace("/", "_")}_$code.pdf")
        return try {
            val isThermal = code == "COMPACT_POS" || code == "THERMAL_58" ||
                template?.paperSize == InvoicePaperSize.THERMAL_58 ||
                template?.paperSize == InvoicePaperSize.THERMAL_80
            val isA5 = template?.paperSize == InvoicePaperSize.A5 || code == "MINIMAL_CLEAN"

            val pageWidth = when {
                code == "THERMAL_58" || template?.paperSize == InvoicePaperSize.THERMAL_58 -> 300
                isThermal -> 380
                isA5 -> 420
                else -> 595
            }
            val pageHeight = when {
                isThermal -> (480 + items.size * 36).coerceAtLeast(520)
                isA5 -> 595
                else -> 842
            }

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val currency = profile?.currencySymbol ?: "₹"
            val showLogo = template?.showLogo != false
            val logoBmp = if (showLogo && !isThermal) {
                BrandingAssetManager.decodeDownsampledBitmap(context, profile?.logoUri, 128, 128)
                    ?: runCatching {
                        android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.drawable.ic_app_logo)
                    }.getOrNull()
            } else null

            val signatureBmp = if (!isThermal && template?.showSignature != false) {
                BrandingAssetManager.decodeDownsampledBitmap(context, profile?.signatureUri, 280, 110)
            } else null

            val sealBmp = if (!isThermal && template?.showSignature != false) {
                BrandingAssetManager.decodeDownsampledBitmap(context, profile?.sealUri, 128, 128)
            } else null

            if (code.startsWith("CORPORATE_WAVE") && !isThermal) {
                drawCorporateWaveInvoicePdf(
                    canvas = canvas,
                    pageWidth = pageWidth.toFloat(),
                    pageHeight = pageHeight.toFloat(),
                    profile = profile,
                    invoice = invoice,
                    items = items,
                    template = template,
                    logoBmp = logoBmp,
                    signatureBmp = signatureBmp,
                    sealBmp = sealBmp,
                    currency = currency
                )
            } else {
                val accentInt = parseHexColorSafe(template?.accentColorHex ?: "#EA580C", Color.rgb(234, 88, 12))
                val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentInt; style = Paint.Style.FILL }
                val accentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentInt; style = Paint.Style.STROKE; strokeWidth = 2f }
                val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 24, 27); textSize = if (isThermal) 10.5f else 11.5f }
                val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(24, 24, 27)
                    textSize = if (isThermal) 11.5f else 13.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 116, 139); textSize = if (isThermal) 8.5f else 9.5f }
                val whiteBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = if (isThermal) 13f else 17f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }

                val left = if (isThermal) 16f else 28f
                val right = pageWidth - left
                var y = 0f

                val bizName = profile?.businessName ?: "HisabPulse Business"
                val showBizName = template?.showBusinessName != false
                val showAddress = template?.showAddress != false
                val showGstin = template?.showGstin != false

                val scaledLogo = logoBmp?.let { android.graphics.Bitmap.createScaledBitmap(it, 44, 44, true) }
                val logoPos = template?.logoPosition ?: "LEFT"

                when (code) {
                    "MODERN_ORANGE", "MODERN_RETAIL", "WHOLESALE_PRO" -> {
                        val bannerH = if (isThermal) 68f else 88f
                        canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerH, accentPaint)

                        var textLeft = left
                        if (scaledLogo != null) {
                            val logoX = when (logoPos) {
                                "CENTER" -> (pageWidth - 44f) / 2f
                                "RIGHT" -> right - 54f
                                else -> left
                            }
                            canvas.drawBitmap(scaledLogo, logoX, 22f, null)
                            if (logoPos == "LEFT") textLeft += 52f
                        }

                        if (showBizName) {
                            canvas.drawText(bizName.take(28), textLeft, 34f, whiteBoldPaint)
                        }
                        val subWhite = Paint(mutedPaint).apply { color = Color.rgb(255, 245, 238); textSize = 9.5f }
                        val subLine = buildList {
                            if (!profile?.phone.isNullOrBlank()) add(profile!!.phone)
                            if (showGstin && !profile?.gstin.isNullOrBlank()) add("GSTIN: ${profile!!.gstin}")
                        }.joinToString("  |  ")
                        canvas.drawText(subLine, textLeft, 54f, subWhite)
                        if (!isThermal) {
                            canvas.drawText(invoice.documentType.label.uppercase(), right - 165f, 36f, Paint(whiteBoldPaint).apply { textSize = 12f })
                            canvas.drawText("#${invoice.invoiceNumber}", right - 165f, 54f, subWhite)
                        }
                        y = bannerH + 20f
                    }
                    "CLASSIC_GST", "DETAILED_GST" -> {
                        y = 26f
                        canvas.drawRect(left - 6f, 16f, right + 6f, pageHeight - 16f, accentStroke)
                        if (showBizName) canvas.drawText(bizName, left + 4f, y + 10f, boldPaint)
                        y += 22f
                        if (showAddress && !profile?.address.isNullOrBlank()) {
                            canvas.drawText(profile!!.address, left + 4f, y, mutedPaint)
                            y += 13f
                        }
                        if (showGstin && !profile?.gstin.isNullOrBlank()) {
                            canvas.drawText("GSTIN: ${profile!!.gstin}", left + 4f, y, mutedPaint)
                            y += 14f
                        }
                        canvas.drawText("${invoice.documentType.label}  #${invoice.invoiceNumber}", left + 4f, y, Paint(boldPaint).apply {
                            color = accentInt
                            textSize = 12f
                        })
                        y += 10f
                        canvas.drawLine(left, y, right, y, linePaint)
                        y += 16f
                    }
                    else -> {
                        y = 28f
                        if (showBizName) {
                            canvas.drawText(bizName, left, y, boldPaint)
                            y += 16f
                        }
                        if (showAddress && !profile?.address.isNullOrBlank()) {
                            canvas.drawText(profile!!.address.take(36), left, y, mutedPaint)
                            y += 12f
                        }
                        if (showGstin && !profile?.gstin.isNullOrBlank()) {
                            canvas.drawText("GSTIN: ${profile!!.gstin}", left, y, mutedPaint)
                            y += 12f
                        }
                        canvas.drawText("${invoice.documentType.label} #${invoice.invoiceNumber}", left, y, Paint(boldPaint).apply { textSize = 11f })
                        y += 10f
                        canvas.drawLine(left, y, right, y, linePaint)
                        y += 14f
                    }
                }

                if (!template?.headerText.isNullOrBlank()) {
                    canvas.drawText(template!!.headerText.take(64), left, y, mutedPaint)
                    y += 13f
                }

                canvas.drawText("Bill To: ${invoice.customerName.take(28)}", left, y, Paint(boldPaint).apply { textSize = 10.5f })
                if (!isThermal) {
                    canvas.drawText("Date: ${DateUtils.formatDate(invoice.invoiceDateMillis)}", right - 120f, y, darkPaint)
                }
                y += 14f
                if (invoice.customerPhone.isNotBlank()) {
                    canvas.drawText("Ph: ${invoice.customerPhone}", left, y, mutedPaint)
                }
                canvas.drawText(
                    if (isThermal) DateUtils.formatDate(invoice.invoiceDateMillis) else "Status: ${invoice.status.label}",
                    if (isThermal) left + 95f else right - 120f,
                    y,
                    mutedPaint
                )
                y += 14f
                if (showGstin && invoice.customerGstin.isNotBlank()) {
                    canvas.drawText("Party GSTIN: ${invoice.customerGstin}", left, y, mutedPaint)
                    y += 13f
                }
                canvas.drawLine(left, y, right, y, linePaint)
                y += 15f

                val headerBg = Paint().apply { color = Color.rgb(248, 250, 252) }
                canvas.drawRect(left, y - 11f, right, y + 6f, headerBg)
                canvas.drawText("Item", left + 3f, y, Paint(boldPaint).apply { textSize = 9.5f })
                if (!isThermal && !isA5) {
                    if (template?.showHsn != false) canvas.drawText("HSN", left + 160f, y, Paint(boldPaint).apply { textSize = 9.5f })
                    canvas.drawText("Qty", left + 225f, y, Paint(boldPaint).apply { textSize = 9.5f })
                    canvas.drawText("Rate", left + 285f, y, Paint(boldPaint).apply { textSize = 9.5f })
                    if (template?.showTaxBreakdown != false) canvas.drawText("Tax", left + 365f, y, Paint(boldPaint).apply { textSize = 9.5f })
                }
                canvas.drawText("Amount", right - 64f, y, Paint(boldPaint).apply { textSize = 9.5f })
                y += 16f

                for (item in items) {
                    if (y > pageHeight - 165f) break
                    val qtyStr = Money.formatQty(Money.fromPaise(item.quantityScaled))
                    val rateStr = Money.formatPaise(item.ratePaise, currency)
                    val taxStr = Money.formatPaise(item.cgstPaise + item.sgstPaise + item.igstPaise, currency)
                    val amtStr = Money.formatPaise(item.lineTotalPaise, currency)

                    canvas.drawText(item.productName.take(if (isThermal) 22 else 26), left + 3f, y, Paint(darkPaint).apply { textSize = 9.5f })
                    if (!isThermal && !isA5) {
                        if (template?.showHsn != false) canvas.drawText(item.hsnSacCode.take(8), left + 160f, y, mutedPaint)
                        canvas.drawText("$qtyStr ${item.unitName}", left + 225f, y, Paint(darkPaint).apply { textSize = 9.5f })
                        canvas.drawText(rateStr, left + 285f, y, Paint(darkPaint).apply { textSize = 9.5f })
                        if (template?.showTaxBreakdown != false) canvas.drawText(taxStr, left + 365f, y, Paint(darkPaint).apply { textSize = 9.5f })
                    } else {
                        canvas.drawText("$qtyStr x $rateStr", left + 3f, y + 10f, mutedPaint)
                    }
                    canvas.drawText(amtStr, right - 64f, y, Paint(darkPaint).apply { textSize = 9.5f })
                    y += if (isThermal || isA5) 24f else 17f
                }

                y += 6f
                canvas.drawLine(left, y, right, y, linePaint)
                y += 16f

                val summaryX = (right - 175f).coerceAtLeast(left)
                fun drawTotalLine(label: String, value: String, bold: Boolean = false) {
                    val p = if (bold) Paint(boldPaint).apply { textSize = 11f } else Paint(darkPaint).apply { textSize = 9.5f }
                    canvas.drawText(label, summaryX, y, p)
                    canvas.drawText(value, right - 68f, y, p)
                    y += 14f
                }

                drawTotalLine("Subtotal:", Money.formatPaise(invoice.subtotalPaise, currency))
                if (template?.showDiscountColumn != false && (invoice.itemDiscountPaise + invoice.invoiceDiscountFlatPaise > 0L)) {
                    drawTotalLine("Discount:", "-${Money.formatPaise(invoice.itemDiscountPaise + invoice.invoiceDiscountFlatPaise, currency)}")
                }
                if (template?.showTaxBreakdown != false) {
                    if (invoice.cgstPaise > 0L) drawTotalLine("CGST:", Money.formatPaise(invoice.cgstPaise, currency))
                    if (invoice.sgstPaise > 0L) drawTotalLine("SGST:", Money.formatPaise(invoice.sgstPaise, currency))
                    if (invoice.igstPaise > 0L) drawTotalLine("IGST:", Money.formatPaise(invoice.igstPaise, currency))
                }
                if (invoice.roundOffPaise != 0L) drawTotalLine("Round Off:", Money.formatPaise(invoice.roundOffPaise, currency))
                drawTotalLine("Grand Total:", Money.formatPaise(invoice.grandTotalPaise, currency), bold = true)
                if (template?.showPaymentInfo != false) {
                    drawTotalLine("Paid:", Money.formatPaise(invoice.paidAmountPaise, currency))
                    drawTotalLine("Balance Due:", Money.formatPaise(invoice.balanceDuePaise, currency), bold = true)
                }

                if (!isThermal && template?.showUpiQr != false && !profile?.upiId.isNullOrBlank() && invoice.balanceDuePaise > 0L) {
                    val payload = BarcodeUtils.buildUpiQrPayload(
                        upiId = profile!!.upiId,
                        payeeName = profile.businessName,
                        amount = Money.fromPaise(invoice.balanceDuePaise),
                        note = invoice.invoiceNumber
                    )
                    BarcodeUtils.generateQrBitmap(payload, 86)?.let { qr ->
                        canvas.drawBitmap(qr, left, (y - 76f).coerceAtLeast(180f), null)
                        canvas.drawText("Scan UPI to Pay", left, y + 18f, mutedPaint)
                    }
                }

                val terms = template?.termsText?.ifBlank { invoice.termsAndConditions } ?: invoice.termsAndConditions
                if (template?.showCustomerNotes != false && invoice.notes.isNotBlank()) {
                    y += 10f
                    canvas.drawText("Note: ${invoice.notes.take(65)}", left, y, mutedPaint)
                }
                if (terms.isNotBlank()) {
                    y += 12f
                    canvas.drawText("Terms: ${terms.take(65)}", left, y, mutedPaint)
                }
                if (!isThermal && template?.showSignature != false) {
                    signatureBmp?.let { sb ->
                        val dst = android.graphics.RectF(right - 130f, pageHeight - 78f, right - 18f, pageHeight - 46f)
                        canvas.drawBitmap(sb, null, dst, null)
                    }
                    canvas.drawText(profile?.signatureLabel ?: "Authorized Signatory", right - 120f, pageHeight - 36f, mutedPaint)
                }
                val footer = template?.footerMessage?.ifBlank { "Generated by HisabPulse" } ?: "Generated by HisabPulse"
                canvas.drawText(footer.take(55), left, pageHeight - 20f, mutedPaint)
            }

            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            outFile
        } catch (_: Throwable) {
            val lines = buildList {
                add("${profile?.businessName ?: "HisabPulse Business"} [${template?.name ?: code}] - ${invoice.documentType.label} #${invoice.invoiceNumber}")
                add("Bill To: ${invoice.customerName} | Date: ${DateUtils.formatDate(invoice.invoiceDateMillis)}")
                items.forEach { item ->
                    add("${item.productName} x ${Money.formatQty(Money.fromPaise(item.quantityScaled))} = Rs ${Money.formatPlain(Money.fromPaise(item.lineTotalPaise))}")
                }
                add("Tax: Rs ${Money.formatPlain(Money.fromPaise(invoice.totalTaxPaise))} | Grand Total: Rs ${Money.formatPlain(Money.fromPaise(invoice.grandTotalPaise))}")
            }
            writeFallbackPdf14(outFile, lines)
        }
    }

    private fun drawCorporateWaveInvoicePdf(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        profile: BusinessProfile?,
        invoice: Invoice,
        items: List<InvoiceItem>,
        template: InvoiceTemplate?,
        logoBmp: android.graphics.Bitmap?,
        signatureBmp: android.graphics.Bitmap?,
        sealBmp: android.graphics.Bitmap?,
        currency: String
    ) {
        val navyInt = parseHexColorSafe(template?.accentColorHex ?: "#0F2D5C", Color.rgb(15, 45, 92))
        val blueInt = parseHexColorSafe(template?.secondaryColorHex ?: "#0284C7", Color.rgb(2, 132, 199))
        val scale = pageWidth / 595f
        val left = 26f * scale
        val right = pageWidth - left
        val contentWidth = right - left

        val navyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navyInt; style = Paint.Style.FILL }
        val blueFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blueInt; style = Paint.Style.FILL }
        val navyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navyInt; style = Paint.Style.STROKE; strokeWidth = 1.1f * scale }
        val blueStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blueInt; style = Paint.Style.STROKE; strokeWidth = 1.2f * scale }
        val gridStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(148, 163, 184); style = Paint.Style.STROKE; strokeWidth = 0.7f * scale }
        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleNavy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyInt
            textSize = 27f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bizPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyInt
            textSize = 14.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.8f * scale
        }
        val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8.8f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 8.2f * scale
        }

        // Top-left corporate wave
        val topOuter = android.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(pageWidth * 0.56f, 0f)
            cubicTo(pageWidth * 0.36f, 12f * scale, pageWidth * 0.17f, 40f * scale, 0f, 78f * scale)
            close()
        }
        val topInner = android.graphics.Path().apply {
            moveTo(0f, 78f * scale)
            cubicTo(pageWidth * 0.14f, 42f * scale, pageWidth * 0.34f, 14f * scale, pageWidth * 0.58f, 0f)
            lineTo(pageWidth * 0.48f, 0f)
            cubicTo(pageWidth * 0.27f, 18f * scale, pageWidth * 0.12f, 48f * scale, 0f, 86f * scale)
            close()
        }
        canvas.drawPath(topOuter, navyFill)
        canvas.drawPath(topInner, blueFill)

        // Bottom-right corporate wave
        val botBlue = android.graphics.Path().apply {
            moveTo(pageWidth * 0.48f, pageHeight)
            cubicTo(pageWidth * 0.68f, pageHeight - 24f * scale, pageWidth * 0.85f, pageHeight - 48f * scale, pageWidth, pageHeight - 86f * scale)
            lineTo(pageWidth, pageHeight)
            close()
        }
        val botNavy = android.graphics.Path().apply {
            moveTo(pageWidth * 0.60f, pageHeight)
            cubicTo(pageWidth * 0.77f, pageHeight - 18f * scale, pageWidth * 0.90f, pageHeight - 40f * scale, pageWidth, pageHeight - 66f * scale)
            lineTo(pageWidth, pageHeight)
            close()
        }
        canvas.drawPath(botBlue, blueFill)
        canvas.drawPath(botNavy, navyFill)

        // Header left: Logo + Business info
        val headerTop = 44f * scale
        var infoX = left
        if (logoBmp != null && template?.showLogo != false) {
            val logoSize = 46f * scale
            val dstLogo = android.graphics.RectF(left, headerTop, left + logoSize, headerTop + logoSize)
            canvas.drawBitmap(logoBmp, null, dstLogo, null)
            infoX += logoSize + 10f * scale
        }

        var hy = headerTop + 13f * scale
        val bizName = (profile?.businessName ?: "HisabPulse Business").ifBlank { "HisabPulse Business" }
        if (template?.showBusinessName != false) {
            canvas.drawText(bizName.take(28), infoX, hy, bizPaint)
            hy += 13f * scale
        }
        val tagline = profile?.tagline?.ifBlank { template?.headerText } ?: template?.headerText.orEmpty()
        if (tagline.isNotBlank()) {
            canvas.drawText(tagline.take(38), infoX, hy, bodyPaint)
            hy += 13f * scale
        }

        if (template?.showAddress != false) {
            val fullAddress = listOf(profile?.address.orEmpty(), profile?.city.orEmpty(), profile?.state.orEmpty(), profile?.pinCode.orEmpty())
                .map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
            if (fullAddress.isNotBlank()) {
                canvas.drawText("Loc: ${fullAddress.take(42)}", infoX, hy, mutedPaint)
                hy += 11.5f * scale
            }
            if (!profile?.phone.isNullOrBlank()) {
                canvas.drawText("Tel: ${profile!!.phone}", infoX, hy, mutedPaint)
                hy += 11.5f * scale
            }
            if (!profile?.email.isNullOrBlank()) {
                canvas.drawText("Mail: ${profile!!.email}", infoX, hy, mutedPaint)
                hy += 11.5f * scale
            }
            if (!profile?.website.isNullOrBlank()) {
                canvas.drawText("Web: ${profile!!.website}", infoX, hy, mutedPaint)
                hy += 11.5f * scale
            }
        }

        // Vertical divider
        val divX = left + contentWidth * 0.56f
        val headerBottom = maxOf(hy + 4f * scale, headerTop + 78f * scale)
        canvas.drawLine(divX, headerTop - 6f * scale, divX, headerBottom - 6f * scale, navyStroke)

        // Header right: INVOICE + pill + GSTIN
        val rightColCenter = divX + (right - divX) / 2f
        val invTitlePaint = Paint(titleNavy).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("INVOICE", rightColCenter, headerTop + 26f * scale, invTitlePaint)

        val badgeW = 148f * scale
        val badgeH = 22f * scale
        val badgeRect = android.graphics.RectF(
            rightColCenter - badgeW / 2f,
            headerTop + 34f * scale,
            rightColCenter + badgeW / 2f,
            headerTop + 34f * scale + badgeH
        )
        canvas.drawRoundRect(badgeRect, 7f * scale, 7f * scale, blueFill)
        val badgeTextPaint = Paint(whiteBold).apply { textSize = 11f * scale; textAlign = Paint.Align.CENTER }
        canvas.drawText(invoice.documentType.label.uppercase(), rightColCenter, badgeRect.centerY() + 4f * scale, badgeTextPaint)

        if (template?.showGstin != false && !profile?.gstin.isNullOrBlank()) {
            val gstPaint = Paint(bodyBoldPaint).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("GSTIN : ${profile!!.gstin}", rightColCenter, badgeRect.bottom + 16f * scale, gstPaint)
        }

        // Accent horizontal divider
        var y = headerBottom + 6f * scale
        canvas.drawLine(left, y, right, y, Paint(blueStroke).apply { strokeWidth = 1.6f * scale })
        y += 12f * scale

        // Dual metadata cards: Invoice Details & Bill To
        val cardGap = 12f * scale
        val cardLeftW = contentWidth * 0.46f
        val cardRightW = contentWidth - cardLeftW - cardGap
        val cardHeight = 84f * scale
        val cardHeaderH = 20f * scale

        fun drawTitledBox(x: Float, top: Float, w: Float, h: Float, title: String) {
            val boxRect = android.graphics.RectF(x, top, x + w, top + h)
            val headerRect = android.graphics.RectF(x, top, x + w, top + cardHeaderH)
            canvas.drawRoundRect(boxRect, 6f * scale, 6f * scale, blueStroke)
            canvas.drawRoundRect(headerRect, 6f * scale, 6f * scale, navyFill)
            canvas.drawRect(x, top + cardHeaderH - 5f * scale, x + w, top + cardHeaderH, navyFill)
            canvas.drawText(title, x + 8f * scale, top + 13.5f * scale, whiteBold)
        }

        drawTitledBox(left, y, cardLeftW, cardHeight, "Invoice Details")
        val rightCardX = left + cardLeftW + cardGap
        drawTitledBox(rightCardX, y, cardRightW, cardHeight, "Bill To (Customer Details)")

        var ly = y + cardHeaderH + 14f * scale
        fun drawKv(x: Float, labelX: Float, colonX: Float, valX: Float, k: String, v: String, rowY: Float) {
            canvas.drawText(k, x + labelX, rowY, bodyBoldPaint)
            canvas.drawText(":", x + colonX, rowY, bodyPaint)
            canvas.drawText(v, x + valX, rowY, bodyPaint)
        }
        drawKv(left, 8f * scale, 74f * scale, 82f * scale, "Invoice No.", invoice.invoiceNumber.take(18), ly); ly += 14f * scale
        drawKv(left, 8f * scale, 74f * scale, 82f * scale, "Invoice Date", DateUtils.formatDate(invoice.invoiceDateMillis), ly); ly += 14f * scale
        drawKv(left, 8f * scale, 74f * scale, 82f * scale, "Due Date", invoice.dueDateMillis?.let { DateUtils.formatDate(it) } ?: "On Receipt", ly); ly += 14f * scale
        drawKv(left, 8f * scale, 74f * scale, 82f * scale, "Place of Supply", if (invoice.isInterState) "Inter-State (IGST)" else "Intra-State", ly)

        var ry = y + cardHeaderH + 14f * scale
        drawKv(rightCardX, 8f * scale, 82f * scale, 90f * scale, "Customer Name", invoice.customerName.take(26), ry); ry += 14f * scale
        val custAddr = listOf(invoice.customerAddress, invoice.customerPhone.takeIf { it.isNotBlank() }?.let { "Ph: $it" } ?: "")
            .filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Walk-in / Counter Sale" }
        drawKv(rightCardX, 8f * scale, 82f * scale, 90f * scale, "Address", custAddr.take(30), ry); ry += 14f * scale
        if (template?.showGstin != false) {
            drawKv(rightCardX, 8f * scale, 82f * scale, 90f * scale, "GSTIN", invoice.customerGstin.ifBlank { "N/A" }, ry)
        }

        y += cardHeight + 12f * scale

        // Items Table
        val colNoW = 34f * scale
        val colQtyW = 64f * scale
        val colRateW = 78f * scale
        val colAmtW = 86f * scale
        val colDescW = contentWidth - colNoW - colQtyW - colRateW - colAmtW
        val tableHeaderH = 22f * scale
        val rowH = 19f * scale
        val maxRows = ((pageHeight - y - 275f * scale) / rowH).toInt().coerceAtLeast(3)
        val shownItems = items.take(maxRows)
        val displayRowCount = maxOf(shownItems.size, 4)
        val tableH = tableHeaderH + displayRowCount * rowH

        val tableRect = android.graphics.RectF(left, y, right, y + tableH)
        val thRect = android.graphics.RectF(left, y, right, y + tableHeaderH)
        canvas.drawRect(thRect, navyFill)
        canvas.drawRect(tableRect, navyStroke)

        val xNo = left
        val xDesc = xNo + colNoW
        val xQty = xDesc + colDescW
        val xRate = xQty + colQtyW
        val xAmt = xRate + colRateW

        val centerWhite = Paint(whiteBold).apply { textAlign = Paint.Align.CENTER }
        val rightWhite = Paint(whiteBold).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("No.", xNo + colNoW / 2f, y + 14.5f * scale, centerWhite)
        canvas.drawText("Description", xDesc + 8f * scale, y + 14.5f * scale, whiteBold)
        canvas.drawText("Qty", xQty + colQtyW / 2f, y + 14.5f * scale, centerWhite)
        canvas.drawText("Rate", xRate + colRateW - 6f * scale, y + 14.5f * scale, rightWhite)
        canvas.drawText("Amount", right - 8f * scale, y + 14.5f * scale, rightWhite)

        // Vertical dividers
        listOf(xDesc, xQty, xRate, xAmt).forEach { vx ->
            canvas.drawLine(vx, y, vx, y + tableH, gridStroke)
        }

        val centerBody = Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER }
        val rightBody = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        for (i in 0 until displayRowCount) {
            val rowTop = y + tableHeaderH + i * rowH
            canvas.drawLine(left, rowTop, right, rowTop, gridStroke)
            if (i < shownItems.size) {
                val it = shownItems[i]
                val baseline = rowTop + 13f * scale
                canvas.drawText("${i + 1}", xNo + colNoW / 2f, baseline, centerBody)
                val desc = if (template?.showHsn != false && it.hsnSacCode.isNotBlank()) {
                    "${it.productName} (${it.hsnSacCode})"
                } else it.productName
                canvas.drawText(desc.take(36), xDesc + 6f * scale, baseline, bodyPaint)
                val qtyStr = "${Money.formatQty(Money.fromPaise(it.quantityScaled))} ${it.unitName}"
                canvas.drawText(qtyStr.take(10), xQty + colQtyW / 2f, baseline, centerBody)
                canvas.drawText(Money.formatPaise(it.ratePaise, currency), xRate + colRateW - 6f * scale, baseline, rightBody)
                canvas.drawText(Money.formatPaise(it.lineTotalPaise, currency), right - 8f * scale, baseline, rightBody)
            }
        }

        y += tableH + 14f * scale

        // Bottom 2-column Payment Details + Totals Summary Table
        val payBoxW = contentWidth * 0.45f
        val totalsBoxX = left + payBoxW + 16f * scale
        val totalsBoxW = right - totalsBoxX

        if (template?.showPaymentInfo != false) {
            val payH = 74f * scale
            drawTitledBox(left, y, payBoxW, payH, "Payment Details")
            var py = y + cardHeaderH + 14f * scale
            drawKv(left, 8f * scale, 76f * scale, 84f * scale, "Payment Mode", invoice.paymentMode.label, py); py += 15f * scale
            drawKv(left, 8f * scale, 76f * scale, 84f * scale, "Amount Paid", Money.formatPaise(invoice.paidAmountPaise, currency), py); py += 15f * scale
            drawKv(left, 8f * scale, 76f * scale, 84f * scale, "Balance Due", Money.formatPaise(invoice.balanceDuePaise, currency), py)
        }

        if (template?.showUpiQr != false && !profile?.upiId.isNullOrBlank() && invoice.balanceDuePaise > 0L) {
            val payload = BarcodeUtils.buildUpiQrPayload(
                upiId = profile!!.upiId,
                payeeName = profile.businessName,
                amount = Money.fromPaise(invoice.balanceDuePaise),
                note = invoice.invoiceNumber
            )
            BarcodeUtils.generateQrBitmap(payload, 76)?.let { qr ->
                canvas.drawBitmap(qr, left, y + 80f * scale, null)
                canvas.drawText("Scan UPI to Pay", left + 82f * scale, y + 108f * scale, mutedPaint)
            }
        }

        val summaryRows = buildList {
            add("Subtotal" to Money.formatPaise(invoice.subtotalPaise, currency))
            if (template?.showDiscountColumn != false) {
                val disc = invoice.itemDiscountPaise + invoice.invoiceDiscountFlatPaise + invoice.loyaltyDiscountPaise
                add("Discount" to if (disc > 0L) "-${Money.formatPaise(disc, currency)}" else "${currency}0.00")
            }
            if (template?.showTaxBreakdown != false) {
                add("CGST" to Money.formatPaise(invoice.cgstPaise, currency))
                add("SGST" to Money.formatPaise(invoice.sgstPaise, currency))
                if (invoice.igstPaise > 0L) add("IGST" to Money.formatPaise(invoice.igstPaise, currency))
            }
            if (invoice.roundOffPaise != 0L) {
                add("Round Off" to Money.formatPaise(invoice.roundOffPaise, currency))
            }
        }
        val sRowH = 18f * scale
        val grandH = 24f * scale
        val sumTableH = summaryRows.size * sRowH + grandH
        val sumMidX = totalsBoxX + totalsBoxW * 0.56f

        canvas.drawRect(totalsBoxX, y, right, y + sumTableH, navyStroke)
        canvas.drawLine(sumMidX, y, sumMidX, y + sumTableH - grandH, gridStroke)

        var sy = y
        summaryRows.forEachIndexed { idx, (lbl, valStr) ->
            if (idx > 0) canvas.drawLine(totalsBoxX, sy, right, sy, gridStroke)
            canvas.drawText(lbl, totalsBoxX + 8f * scale, sy + 12.5f * scale, bodyBoldPaint)
            canvas.drawText(valStr, right - 8f * scale, sy + 12.5f * scale, rightBody)
            sy += sRowH
        }
        canvas.drawRect(totalsBoxX, sy, right, sy + grandH, navyFill)
        canvas.drawText("Grand Total", totalsBoxX + 8f * scale, sy + 16f * scale, Paint(whiteBold).apply { textSize = 11f * scale })
        canvas.drawText(Money.formatPaise(invoice.grandTotalPaise, currency), right - 8f * scale, sy + 16f * scale, Paint(whiteBold).apply {
            textSize = 11.5f * scale
            textAlign = Paint.Align.RIGHT
        })

        y = maxOf(y + sumTableH, y + 86f * scale) + 14f * scale

        // Terms & Conditions Card
        val termsStr = template?.termsText?.ifBlank { invoice.termsAndConditions } ?: invoice.termsAndConditions.ifBlank {
            "1. Goods once sold will not be taken back.\n2. Payment should be made within the due date.\n3. Thank you for your business."
        }
        val termLines = termsStr.lines().filter { it.isNotBlank() }.take(3)
        val termsBoxH = cardHeaderH + (termLines.size.coerceAtLeast(1) * 13f + 12f) * scale
        if (y + termsBoxH < pageHeight - 115f * scale) {
            drawTitledBox(left, y, contentWidth, termsBoxH, "Terms & Conditions")
            var ty = y + cardHeaderH + 14f * scale
            termLines.forEach { line ->
                canvas.drawText(line.take(92), left + 8f * scale, ty, bodyPaint)
                ty += 13f * scale
            }
            y += termsBoxH + 14f * scale
        }

        // Dual Signature Cards near bottom
        val sigCardY = minOf(y, pageHeight - 108f * scale)
        if (template?.showSignature != false) {
            val sigW = contentWidth * 0.41f
            val sigH = 54f * scale
            val leftSigRect = android.graphics.RectF(left, sigCardY, left + sigW, sigCardY + sigH)
            val rightSigRect = android.graphics.RectF(right - sigW, sigCardY, right, sigCardY + sigH)
            canvas.drawRoundRect(leftSigRect, 8f * scale, 8f * scale, blueStroke)
            canvas.drawRoundRect(rightSigRect, 8f * scale, 8f * scale, blueStroke)

            // Left: Customer Signature
            canvas.drawLine(left + 50f * scale, sigCardY + 32f * scale, left + sigW - 14f * scale, sigCardY + 32f * scale, gridStroke)
            val centerNavy = Paint(bodyBoldPaint).apply { color = navyInt; textAlign = Paint.Align.CENTER; textSize = 9f * scale }
            canvas.drawText("Customer Signature", left + 50f * scale + (sigW - 64f * scale) / 2f, sigCardY + 45f * scale, centerNavy)

            // Right: Authorized Signature & Seal
            val sealAreaW = 42f * scale
            if (sealBmp != null) {
                val dstSeal = android.graphics.RectF(rightSigRect.left + 10f * scale, sigCardY + 10f * scale, rightSigRect.left + 40f * scale, sigCardY + 40f * scale)
                canvas.drawBitmap(sealBmp, null, dstSeal, null)
            } else {
                canvas.drawCircle(rightSigRect.left + 26f * scale, sigCardY + 25f * scale, 12f * scale, blueStroke)
                canvas.drawCircle(rightSigRect.left + 26f * scale, sigCardY + 25f * scale, 7f * scale, navyFill)
            }
            val sigLineStart = rightSigRect.left + sealAreaW + 8f * scale
            val sigLineEnd = rightSigRect.right - 14f * scale
            if (signatureBmp != null) {
                val dstSig = android.graphics.RectF(sigLineStart + 6f * scale, sigCardY + 4f * scale, sigLineEnd - 6f * scale, sigCardY + 30f * scale)
                canvas.drawBitmap(signatureBmp, null, dstSig, null)
            }
            canvas.drawLine(sigLineStart, sigCardY + 32f * scale, sigLineEnd, sigCardY + 32f * scale, gridStroke)
            canvas.drawText(
                (profile?.signatureLabel ?: "Authorized Signature").take(24),
                (sigLineStart + sigLineEnd) / 2f,
                sigCardY + 42f * scale,
                centerNavy
            )
            val subSeal = Paint(mutedPaint).apply { color = blueInt; textAlign = Paint.Align.CENTER; textSize = 7.5f * scale }
            canvas.drawText("(Seal / Stamp)", (sigLineStart + sigLineEnd) / 2f, sigCardY + 51f * scale, subSeal)
        }

        // Closing Footer Banner
        val footerY = pageHeight - 28f * scale
        val footerMsg = template?.footerMessage?.ifBlank { "Thank You For Your Business!" } ?: "Thank You For Your Business!"
        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyInt
            textSize = 11.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
        }
        val textW = footerTextPaint.measureText(footerMsg.take(36))
        val midX = pageWidth / 2f
        canvas.drawLine(left, footerY - 3f * scale, (midX - textW / 2f - 12f * scale).coerceAtLeast(left), footerY - 3f * scale, navyStroke)
        canvas.drawText(footerMsg.take(36), midX, footerY, footerTextPaint)
        canvas.drawLine((midX + textW / 2f + 12f * scale).coerceAtMost(right), footerY - 3f * scale, right, footerY - 3f * scale, navyStroke)
    }

    fun generateDailyClosingPdf(
        context: Context,
        profile: BusinessProfile?,
        closing: DailyClosing
    ): File {
        return generateTabularReportPdf(
            context = context,
            fileName = "DailyClosing_${closing.closingDateIso}.pdf",
            title = "${profile?.businessName ?: "HisabPulse"} — Daily Closing (${closing.closingDateIso})",
            subtitle = "Generated ${DateUtils.formatDateTime(closing.createdAt)}",
            headers = listOf("Metric", "Amount"),
            rows = listOf(
                listOf("Opening Cash", "Rs ${Money.formatPlain(Money.fromPaise(closing.openingCashPaise))}"),
                listOf("Cash Sales", "Rs ${Money.formatPlain(Money.fromPaise(closing.cashSalesPaise))}"),
                listOf("Cash Receipts", "Rs ${Money.formatPlain(Money.fromPaise(closing.cashReceiptsPaise))}"),
                listOf("Cash Expenses", "Rs ${Money.formatPlain(Money.fromPaise(closing.cashExpensesPaise))}"),
                listOf("Cash Supplier Payments", "Rs ${Money.formatPlain(Money.fromPaise(closing.cashPaymentsPaise))}"),
                listOf("Expected Closing Cash", "Rs ${Money.formatPlain(Money.fromPaise(closing.expectedClosingCashPaise))}"),
                listOf("Actual Counted Cash", "Rs ${Money.formatPlain(Money.fromPaise(closing.actualCountedCashPaise))}"),
                listOf("Cash Difference", "Rs ${Money.formatPlain(Money.fromPaise(closing.cashDifferencePaise))}"),
                listOf("UPI Collections", "Rs ${Money.formatPlain(Money.fromPaise(closing.upiCollectionsPaise))}"),
                listOf("Bank / Card Collections", "Rs ${Money.formatPlain(Money.fromPaise(closing.bankCollectionsPaise))}"),
                listOf("Credit Sales", "Rs ${Money.formatPlain(Money.fromPaise(closing.creditSalesPaise))}"),
                listOf("Total Sales", "Rs ${Money.formatPlain(Money.fromPaise(closing.totalSalesPaise))}"),
                listOf("Total Expenses", "Rs ${Money.formatPlain(Money.fromPaise(closing.totalExpensesPaise))}")
            )
        )
    }

    fun generateTabularReportPdf(
        context: Context,
        fileName: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        footerNote: String = "Generated offline by HisabPulse"
    ): File {
        val outFile = File(exportsDir(context), fileName)
        return try {
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(234, 88, 12)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint().apply { color = Color.rgb(71, 85, 105); textSize = 10f }
            val headPaint = Paint().apply {
                color = Color.rgb(24, 24, 27)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cellPaint = Paint().apply { color = Color.rgb(30, 41, 59); textSize = 9.5f }
            val linePaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }

            var y = 40f
            canvas.drawText(title, 28f, y, titlePaint)
            y += 16f
            canvas.drawText(subtitle, 28f, y, subPaint)
            y += 18f

            val colWidth = (pageWidth - 56f) / headers.size.coerceAtLeast(1)
            fun drawHeaders() {
                headers.forEachIndexed { idx, h ->
                    canvas.drawText(h.take(18), 28f + idx * colWidth, y, headPaint)
                }
                y += 6f
                canvas.drawLine(28f, y, pageWidth - 28f, y, linePaint)
                y += 14f
            }
            drawHeaders()

            for (row in rows) {
                if (y > pageHeight - 60f) {
                    canvas.drawText(footerNote, 28f, pageHeight - 24f, subPaint)
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = 40f
                    drawHeaders()
                }
                row.forEachIndexed { idx, cell ->
                    canvas.drawText(cell.take(20), 28f + idx * colWidth, y, cellPaint)
                }
                y += 15f
            }

            canvas.drawText(footerNote, 28f, pageHeight - 24f, subPaint)
            pdf.finishPage(page)

            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            outFile
        } catch (_: Throwable) {
            val lines = buildList {
                add(title)
                add(subtitle)
                add(headers.joinToString(" | "))
                rows.take(40).forEach { add(it.joinToString(" | ")) }
                add(footerNote)
            }
            writeFallbackPdf14(outFile, lines)
        }
    }

    private fun writeFallbackPdf14(outFile: File, lines: List<String>): File {
        fun escPdf(s: String) = s
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace(Regex("[^\\x20-\\x7E]"), " ")

        val streamOps = buildString {
            append("BT\n/F1 11 Tf\n40 780 Td\n16 TL\n")
            lines.forEachIndexed { idx, line ->
                if (idx > 0) append("T*\n")
                append("(${escPdf(line)}) Tj\n")
            }
            append("ET\n")
        }
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
            "4 0 obj\n<< /Length ${streamOps.length} >>\nstream\n${streamOps}endstream\nendobj\n",
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        for (obj in objects) {
            offsets += sb.length
            sb.append(obj)
        }
        val xrefPos = sb.length
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (i in 1..objects.size) {
            sb.append(String.format("%010d 00000 n \n", offsets[i]))
        }
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        outFile.writeText(sb.toString(), Charsets.US_ASCII)
        return outFile
    }

    fun exportCsv(
        context: Context,
        fileName: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        val outFile = File(exportsDir(context), fileName)
        outFile.writeBytes(ExcelXmlUtils.writeCsvBytes(headers, rows))
        return outFile
    }

    fun exportXlsx(
        context: Context,
        fileName: String,
        sheetName: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        val outFile = File(exportsDir(context), fileName)
        outFile.writeBytes(ExcelXmlUtils.writeXlsxBytes(sheetName, headers, rows))
        return outFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
