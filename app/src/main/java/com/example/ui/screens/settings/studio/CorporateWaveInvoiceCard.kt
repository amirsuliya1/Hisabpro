package com.example.ui.screens.settings.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.BarcodeUtils
import com.example.core.BrandingAssetManager
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoicePaperSize
import com.example.data.local.entities.InvoiceTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDecodedAssetBitmap(uriOrPath: String?, maxW: Int = 256, maxH: Int = 256): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, uriOrPath, maxW, maxH) {
        value = if (uriOrPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                BrandingAssetManager.decodeDownsampledBitmap(context, uriOrPath, maxW, maxH)
            }
        }
    }
    return bmp
}

@Composable
fun CorporateWaveInvoiceCard(
    template: InvoiceTemplate,
    businessName: String,
    tagline: String,
    address: String,
    phone: String,
    email: String,
    website: String,
    gstin: String,
    upiId: String,
    signatureLabel: String,
    logoUri: String,
    signatureUri: String,
    sealUri: String,
    currencySymbol: String,
    invoice: Invoice?,
    items: List<InvoiceItem>?,
    modifier: Modifier = Modifier
) {
    val primaryColor = remember(template.accentColorHex) {
        parseColorSafe(template.accentColorHex, Color(0xFF0F2D5C))
    }
    val secondaryColor = remember(template.secondaryColorHex) {
        parseColorSafe(template.secondaryColorHex, Color(0xFF0284C7))
    }

    val logoBmp = rememberDecodedAssetBitmap(logoUri, 256, 256)
    val sigBmp = rememberDecodedAssetBitmap(signatureUri, 420, 160)
    val sealBmp = rememberDecodedAssetBitmap(sealUri, 180, 180)

    val qrBmp by produceState<Bitmap?>(
        initialValue = null,
        upiId,
        businessName,
        invoice?.id,
        invoice?.balanceDuePaise,
        template.showUpiQr
    ) {
        value = if (template.showUpiQr && upiId.isNotBlank() && invoice != null && invoice.balanceDuePaise > 0L) {
            withContext(Dispatchers.Default) {
                val payload = BarcodeUtils.buildUpiQrPayload(
                    upiId = upiId,
                    payeeName = businessName.ifBlank { "HisabPulse Business" },
                    amount = Money.fromPaise(invoice.balanceDuePaise),
                    note = invoice.invoiceNumber
                )
                BarcodeUtils.generateQrBitmap(payload, 220)
            }
        } else null
    }

    val previewMaxWidth = when (template.paperSize) {
        InvoicePaperSize.THERMAL_58 -> 300.dp
        InvoicePaperSize.THERMAL_80 -> 350.dp
        InvoicePaperSize.A5 -> 410.dp
        InvoicePaperSize.A4 -> 620.dp
    }

    val displayItems = remember(items, template.showHsn) {
        if (!items.isNullOrEmpty()) {
            items.mapIndexed { idx, it ->
                CorporateLineRow(
                    no = (idx + 1).toString(),
                    description = if (template.showHsn && it.hsnSacCode.isNotBlank()) {
                        "${it.productName} (HSN: ${it.hsnSacCode})"
                    } else it.productName,
                    qty = "${Money.formatQty(Money.fromPaise(it.quantityScaled))} ${it.unitName}",
                    rate = Money.formatPaise(it.ratePaise, currencySymbol),
                    amount = Money.formatPaise(it.lineTotalPaise, currencySymbol)
                )
            }
        } else {
            listOf(
                CorporateLineRow(
                    no = "1",
                    description = if (template.showHsn) "LED Bulb 9W (HSN: 8539)" else "LED Bulb 9W",
                    qty = "2 PCS",
                    rate = "${currencySymbol}120.00",
                    amount = "${currencySymbol}269.04"
                )
            )
        }
    }

    val bizDisplayName = businessName.ifBlank { "HisabPulse Business" }
    val taglineDisplay = tagline.ifBlank { template.headerText }
    val addrDisplay = address.ifBlank { "Address Line 1, City" }
    val phoneDisplay = phone.ifBlank { "+91 98765 43210" }
    val gstinDisplay = gstin.ifBlank { "27ABCDE1234F1Z5" }

    val invNo = invoice?.invoiceNumber ?: "INV-1001"
    val invDate = invoice?.let { DateUtils.formatDate(it.invoiceDateMillis) } ?: DateUtils.formatDate(System.currentTimeMillis())
    val dueDate = invoice?.dueDateMillis?.let { DateUtils.formatDate(it) } ?: "On Receipt"
    val supplyPlace = invoice?.let { if (it.isInterState) "Inter-State (IGST)" else "Intra-State" } ?: "Intra-State"
    val docBadgeText = invoice?.documentType?.label?.uppercase() ?: "TAX INVOICE"

    val custName = invoice?.customerName ?: "Aarav Traders"
    val custAddress = listOfNotNull(
        invoice?.customerAddress?.takeIf { it.isNotBlank() },
        invoice?.customerPhone?.takeIf { it.isNotBlank() }?.let { "Ph: $it" }
    ).joinToString(" • ").ifBlank { "Customer Address / Phone" }
    val custGstin = invoice?.customerGstin?.ifBlank { "N/A" } ?: "27XYZAB5678G1Z2"

    val subtotalStr = Money.formatPaise(invoice?.subtotalPaise ?: 240_00L, currencySymbol)
    val discountPaise = (invoice?.itemDiscountPaise ?: 12_00L) + (invoice?.invoiceDiscountFlatPaise ?: 0L) + (invoice?.loyaltyDiscountPaise ?: 0L)
    val discountStr = if (discountPaise > 0L) "-${Money.formatPaise(discountPaise, currencySymbol)}" else "${currencySymbol}0.00"
    val cgstStr = Money.formatPaise(invoice?.cgstPaise ?: 20_52L, currencySymbol)
    val sgstStr = Money.formatPaise(invoice?.sgstPaise ?: 20_52L, currencySymbol)
    val igstStr = Money.formatPaise(invoice?.igstPaise ?: 0L, currencySymbol)
    val roundOffPaise = invoice?.roundOffPaise ?: 0L
    val grandTotalStr = Money.formatPaise(invoice?.grandTotalPaise ?: 269_04L, currencySymbol)

    val payModeStr = invoice?.paymentMode?.label ?: "UPI"
    val paidStr = Money.formatPaise(invoice?.paidAmountPaise ?: 269_04L, currencySymbol)
    val balancePaise = invoice?.balanceDuePaise ?: 0L
    val balanceStr = Money.formatPaise(balancePaise, currencySymbol)

    val termsRaw = template.termsText.ifBlank {
        invoice?.termsAndConditions?.ifBlank { null }
            ?: "1. Goods once sold will not be taken back.\n2. Payment should be made within the due date.\n3. Thank you for your business."
    }
    val footerMsg = template.footerMessage.ifBlank { "Thank You For Your Business!" }
    val sigLabelDisplay = signatureLabel.ifBlank { "Authorized Signature" }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .widthIn(max = previewMaxWidth)
                .fillMaxWidth()
                .border(1.dp, primaryColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top-left corporate curved waves
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.TopStart)
                ) {
                    val w = size.width
                    val h = size.height
                    val outerWave = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w * 0.56f, 0f)
                        cubicTo(w * 0.36f, h * 0.14f, w * 0.17f, h * 0.45f, 0f, h * 0.92f)
                        close()
                    }
                    val innerWave = Path().apply {
                        moveTo(0f, h * 0.92f)
                        cubicTo(w * 0.14f, h * 0.48f, w * 0.34f, h * 0.16f, w * 0.58f, 0f)
                        lineTo(w * 0.48f, 0f)
                        cubicTo(w * 0.27f, h * 0.20f, w * 0.12f, h * 0.52f, 0f, h * 1.0f)
                        close()
                    }
                    drawPath(outerWave, color = primaryColor)
                    drawPath(innerWave, color = secondaryColor)
                }

                // Bottom-right corporate curved waves
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    val w = size.width
                    val h = size.height
                    val blueWave = Path().apply {
                        moveTo(w * 0.48f, h)
                        cubicTo(w * 0.68f, h * 0.74f, w * 0.85f, h * 0.44f, w, 0f)
                        lineTo(w, h)
                        close()
                    }
                    val navyWave = Path().apply {
                        moveTo(w * 0.60f, h)
                        cubicTo(w * 0.77f, h * 0.78f, w * 0.90f, h * 0.52f, w, h * 0.22f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(blueWave, color = secondaryColor)
                    drawPath(navyWave, color = primaryColor)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Block
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.22f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (template.showLogo) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .border(1.dp, secondaryColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    ) {
                                        if (logoBmp != null) {
                                            Image(
                                                bitmap = logoBmp.asImageBitmap(),
                                                contentDescription = "Business Logo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_app_logo),
                                                contentDescription = "Default Logo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().padding(3.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    if (template.showBusinessName) {
                                        Text(
                                            text = bizDisplayName,
                                            color = primaryColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            lineHeight = 16.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (taglineDisplay.isNotBlank()) {
                                        Text(
                                            text = taglineDisplay,
                                            color = Color(0xFF334155),
                                            fontSize = 9.5.sp,
                                            lineHeight = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (template.showAddress) {
                                Spacer(modifier = Modifier.height(2.dp))
                                ContactMiniRow(Icons.Default.LocationOn, addrDisplay, primaryColor)
                                ContactMiniRow(Icons.Default.Phone, phoneDisplay, primaryColor)
                                if (email.isNotBlank()) {
                                    ContactMiniRow(Icons.Default.Email, email, primaryColor)
                                }
                                if (website.isNotBlank()) {
                                    ContactMiniRow(Icons.Default.Language, website, primaryColor)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .background(primaryColor.copy(alpha = 0.7f))
                        )

                        Column(
                            modifier = Modifier
                                .weight(0.85f)
                                .padding(start = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "INVOICE",
                                color = primaryColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = secondaryColor,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = docBadgeText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            if (template.showGstin && gstinDisplay.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "GSTIN : $gstinDisplay",
                                    color = Color(0xFF0F172A),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = secondaryColor, thickness = 1.5.dp)

                    // Side-by-side Invoice Details & Bill To Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CorporateSectionCard(
                            title = "Invoice Details",
                            headerBg = primaryColor,
                            borderColor = secondaryColor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            MetaLineRow("Invoice No.", invNo)
                            MetaLineRow("Invoice Date", invDate)
                            MetaLineRow("Due Date", dueDate)
                            MetaLineRow("Place of Supply", supplyPlace)
                        }

                        CorporateSectionCard(
                            title = "Bill To (Customer Details)",
                            headerBg = primaryColor,
                            borderColor = secondaryColor,
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                        ) {
                            MetaLineRow("Customer Name", custName)
                            MetaLineRow("Address", custAddress)
                            if (template.showGstin) {
                                MetaLineRow("GSTIN", custGstin)
                            }
                        }
                    }

                    // Bordered Line Items Table
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, primaryColor, RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(primaryColor)
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell("No.", 0.38f, Color.White, FontWeight.Bold, TextAlign.Center)
                            TableCell("Description", 1.75f, Color.White, FontWeight.Bold, TextAlign.Start)
                            TableCell("Qty", 0.65f, Color.White, FontWeight.Bold, TextAlign.Center)
                            TableCell("Rate", 0.72f, Color.White, FontWeight.Bold, TextAlign.End)
                            TableCell("Amount", 0.85f, Color.White, FontWeight.Bold, TextAlign.End)
                        }
                        displayItems.forEachIndexed { idx, row ->
                            HorizontalDivider(color = primaryColor.copy(alpha = 0.45f), thickness = 0.8.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableCell(row.no, 0.38f, Color(0xFF0F172A), FontWeight.SemiBold, TextAlign.Center)
                                TableCell(row.description, 1.75f, Color(0xFF0F172A), FontWeight.Medium, TextAlign.Start)
                                TableCell(row.qty, 0.65f, Color(0xFF334155), FontWeight.Normal, TextAlign.Center)
                                TableCell(row.rate, 0.72f, Color(0xFF334155), FontWeight.Normal, TextAlign.End)
                                TableCell(row.amount, 0.85f, Color(0xFF0F172A), FontWeight.SemiBold, TextAlign.End)
                            }
                        }
                    }

                    // Payment Details + Summary Table
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(0.95f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (template.showPaymentInfo) {
                                CorporateSectionCard(
                                    title = "Payment Details",
                                    headerBg = primaryColor,
                                    borderColor = secondaryColor,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    MetaLineRow("Payment Mode", payModeStr)
                                    MetaLineRow("Amount Paid", paidStr)
                                    MetaLineRow(
                                        "Balance Due",
                                        balanceStr,
                                        valueColor = if (balancePaise > 0L) Color(0xFFDC2626) else primaryColor
                                    )
                                }
                            }
                            if (template.showUpiQr && upiId.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, secondaryColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (qrBmp != null) {
                                        Image(
                                            bitmap = qrBmp!!.asImageBitmap(),
                                            contentDescription = "UPI QR Code",
                                            modifier = Modifier.size(44.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.QrCode2,
                                            contentDescription = "UPI QR",
                                            tint = primaryColor,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Scan UPI to Pay", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                        Text(upiId, fontSize = 8.sp, color = Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        // Totals Grid Box
                        Column(
                            modifier = Modifier
                                .weight(1.05f)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, primaryColor, RoundedCornerShape(6.dp))
                        ) {
                            SummaryGridRow("Subtotal", subtotalStr, secondaryColor)
                            if (template.showDiscountColumn) {
                                SummaryGridRow("Discount", discountStr, secondaryColor)
                            }
                            if (template.showTaxBreakdown) {
                                SummaryGridRow("CGST", cgstStr, secondaryColor)
                                SummaryGridRow("SGST", sgstStr, secondaryColor)
                                if ((invoice?.igstPaise ?: 0L) > 0L || invoice == null) {
                                    SummaryGridRow("IGST", igstStr, secondaryColor)
                                }
                            }
                            if (roundOffPaise != 0L) {
                                SummaryGridRow("Round Off", Money.formatPaise(roundOffPaise, currencySymbol), secondaryColor)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(primaryColor)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Grand Total", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                Text(grandTotalStr, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp)
                            }
                        }
                    }

                    // Terms & Conditions Card
                    CorporateSectionCard(
                        title = "Terms & Conditions",
                        headerBg = primaryColor,
                        borderColor = secondaryColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        termsRaw.lines().filter { it.isNotBlank() }.take(4).forEach { line ->
                            Text(
                                text = line.trim(),
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                        if (template.showCustomerNotes && !invoice?.notes.isNullOrBlank()) {
                            Text(
                                text = "Note: ${invoice!!.notes}",
                                fontSize = 8.sp,
                                color = primaryColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Dual Signature Cards
                    if (template.showSignature) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Customer Signature Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(1.dp, secondaryColor, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.EditNote,
                                        contentDescription = "Customer Signature Icon",
                                        tint = secondaryColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Spacer(modifier = Modifier.height(18.dp))
                                        HorizontalDivider(color = Color(0xFF475569), thickness = 0.8.dp)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Customer Signature",
                                            color = primaryColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.5.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // Authorized Signature & Seal Card
                            Box(
                                modifier = Modifier
                                    .weight(1.05f)
                                    .fillMaxHeight()
                                    .border(1.dp, secondaryColor, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (sealBmp != null) {
                                        Image(
                                            bitmap = sealBmp.asImageBitmap(),
                                            contentDescription = "Shop Seal",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = "Authorized Seal Icon",
                                            tint = primaryColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (sigBmp != null) {
                                            Image(
                                                bitmap = sigBmp.asImageBitmap(),
                                                contentDescription = "Authorized Signature Image",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(22.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(18.dp))
                                        }
                                        HorizontalDivider(color = Color(0xFF475569), thickness = 0.8.dp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = sigLabelDisplay,
                                            color = primaryColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.5.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "(Seal / Stamp)",
                                            color = secondaryColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 7.5.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Thank You Footer Line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = primaryColor, thickness = 1.dp)
                        Text(
                            text = footerMsg,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = primaryColor, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

private data class CorporateLineRow(
    val no: String,
    val description: String,
    val qty: String,
    val rate: String,
    val amount: String
)

@Composable
private fun ContactMiniRow(icon: ImageVector, text: String, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(11.dp))
        Text(
            text = text,
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            color = Color(0xFF1E293B),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CorporateSectionCard(
    title: String,
    headerBg: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MetaLineRow(label: String, value: String, valueColor: Color = Color(0xFF0F172A)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF334155),
            modifier = Modifier.width(66.dp)
        )
        Text(
            text = ":",
            fontSize = 8.sp,
            color = Color(0xFF475569)
        )
        Text(
            text = value,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color,
    fontWeight: FontWeight,
    textAlign: TextAlign
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 2.dp),
        color = color,
        fontWeight = fontWeight,
        fontSize = 8.5.sp,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SummaryGridRow(label: String, value: String, borderColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
        Text(value, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
    }
    HorizontalDivider(color = borderColor.copy(alpha = 0.35f), thickness = 0.7.dp)
}

fun BusinessProfile?.toFullAddressString(): String {
    if (this == null) return ""
    return listOf(address, city, state, pinCode)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")
}
