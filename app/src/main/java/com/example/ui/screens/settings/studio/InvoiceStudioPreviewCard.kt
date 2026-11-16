package com.example.ui.screens.settings.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entities.InvoicePaperSize
import com.example.data.local.entities.InvoiceTemplate

@Composable
fun InvoiceStudioPreviewCard(
    template: InvoiceTemplate,
    business: StudioEditableBusiness,
    modifier: Modifier = Modifier
) {
    if (template.code.startsWith("CORPORATE_WAVE")) {
        CorporateWaveInvoiceCard(
            template = template,
            businessName = business.businessName,
            tagline = business.tagline,
            address = business.address,
            phone = business.phone,
            email = business.email,
            website = business.website,
            gstin = business.gstin,
            upiId = business.upiId,
            signatureLabel = business.signatureLabel,
            logoUri = business.logoUri,
            signatureUri = business.signatureUri,
            sealUri = business.sealUri,
            currencySymbol = "₹",
            invoice = null,
            items = null,
            modifier = modifier
        )
        return
    }

    val accentColor = remember(template.accentColorHex) {
        parseColorSafe(template.accentColorHex)
    }
    val tintHex = remember(template.accentColorHex) {
        STUDIO_COLOR_SCHEMES.firstOrNull {
            it.primaryHex.equals(template.accentColorHex, ignoreCase = true)
        }?.surfaceTintHex ?: "#F8FAFC"
    }
    val tintColor = remember(tintHex) { parseColorSafe(tintHex, Color(0xFFF8FAFC)) }
    val logoBmp = rememberDecodedAssetBitmap(business.logoUri, 180, 180)
    val sigBmp = rememberDecodedAssetBitmap(business.signatureUri, 240, 90)
    val isClassicBordered = template.code.startsWith("CLASSIC_GST") || template.code.startsWith("DETAILED_GST")
    val isMinimalOrThermal = template.code.startsWith("COMPACT_POS") ||
        template.code.startsWith("THERMAL_58") ||
        template.code.startsWith("MINIMAL_CLEAN") ||
        template.paperSize == InvoicePaperSize.THERMAL_58 ||
        template.paperSize == InvoicePaperSize.THERMAL_80

    val previewMaxWidth = when (template.paperSize) {
        InvoicePaperSize.THERMAL_58 -> 270.dp
        InvoicePaperSize.THERMAL_80 -> 320.dp
        InvoicePaperSize.A5 -> 360.dp
        InvoicePaperSize.A4 -> 600.dp
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = previewMaxWidth)
                .fillMaxWidth()
                .border(
                    width = if (isClassicBordered) 2.dp else 1.dp,
                    color = accentColor,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val headerModifier = when {
                    isClassicBordered || isMinimalOrThermal -> Modifier
                        .fillMaxWidth()
                        .background(tintColor)
                        .padding(14.dp)
                    else -> Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.85f))
                            )
                        )
                        .padding(14.dp)
                }
                val headerTextColor = if (isClassicBordered || isMinimalOrThermal) Color(0xFF0F172A) else Color.White
                val headerSubColor = if (isClassicBordered || isMinimalOrThermal) Color(0xFF475569) else Color.White.copy(alpha = 0.92f)

                Column(
                    modifier = headerModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val bizTitle = business.businessName.ifBlank { "HisabPulse Store" }
                    val addressLine = business.address.ifBlank { "123 Market Road, City" }
                    val gstinLine = business.gstin.ifBlank { "27ABCDE1234F1Z5" }

                    @Composable
                    fun LogoBox() {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            if (logoBmp != null) {
                                Image(
                                    bitmap = logoBmp.asImageBitmap(),
                                    contentDescription = "Business Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = "Default Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().padding(3.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }

                    @Composable
                    fun BusinessInfoBlock(align: TextAlign = TextAlign.Start) {
                        Column(
                            horizontalAlignment = when (align) {
                                TextAlign.Center -> Alignment.CenterHorizontally
                                TextAlign.End -> Alignment.End
                                else -> Alignment.Start
                            }
                        ) {
                            if (template.showBusinessName) {
                                Text(
                                    text = bizTitle,
                                    color = headerTextColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = align
                                )
                            }
                            if (business.tagline.isNotBlank()) {
                                Text(
                                    text = business.tagline,
                                    color = headerSubColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = align
                                )
                            }
                            if (template.showAddress) {
                                Text(
                                    text = addressLine,
                                    color = headerSubColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = align
                                )
                                val contactLine = listOfNotNull(
                                    business.phone.takeIf { it.isNotBlank() }?.let { "Ph: $it" },
                                    business.email.takeIf { it.isNotBlank() },
                                    business.website.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (contactLine.isNotBlank()) {
                                    Text(
                                        text = contactLine,
                                        color = headerSubColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = align
                                    )
                                }
                            }
                            if (template.showGstin) {
                                Text(
                                    text = "GSTIN: $gstinLine",
                                    color = headerSubColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = align
                                )
                            }
                        }
                    }

                    when (template.logoPosition) {
                        "CENTER" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (template.showLogo) LogoBox()
                                BusinessInfoBlock(TextAlign.Center)
                            }
                        }
                        "RIGHT" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) { BusinessInfoBlock(TextAlign.Start) }
                                if (template.showLogo) {
                                    Spacer(modifier = Modifier.size(10.dp))
                                    LogoBox()
                                }
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (template.showLogo) {
                                    LogoBox()
                                    Spacer(modifier = Modifier.size(10.dp))
                                }
                                Box(modifier = Modifier.weight(1f)) { BusinessInfoBlock(TextAlign.Start) }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (template.headerText.isNotBlank()) {
                        Surface(
                            color = tintColor,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = template.headerText,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TAX INVOICE #INV-1001",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Surface(
                            color = tintColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = template.paperSize.label,
                                color = accentColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text("Bill To: Aarav Traders • 9876543210", style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tintColor)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (template.showHsn) "Item (HSN)" else "Item", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(if (template.showDiscountColumn) "Qty • Disc" else "Qty", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("Total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (template.showHsn) "LED Bulb 9W (8539)" else "LED Bulb 9W", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E293B))
                        Text(if (template.showDiscountColumn) "2 x ₹120 (-5%)" else "2 x ₹120", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                        Text("₹228.00", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
                    }

                    if (template.showTaxBreakdown) {
                        Text("CGST 9%: ₹20.52 • SGST 9%: ₹20.52", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("₹269.00", fontWeight = FontWeight.ExtraBold, color = accentColor)
                    }

                    if (template.showPaymentInfo) {
                        Text("Paid via UPI • Balance Due: ₹0.00", style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                    }

                    if (template.showUpiQr) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Text(
                                text = "UPI QR: ${business.upiId.ifBlank { "shop@upi" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }

                    if (template.showCustomerNotes) {
                        Text("Note: Goods once sold will be replaced within 7 days.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    }

                    if (template.termsText.isNotBlank()) {
                        Text("Terms: ${template.termsText}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(
                            template.footerMessage.ifBlank { "Thank you for your business!" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF475569),
                            modifier = Modifier.weight(1f)
                        )
                        if (template.showSignature) {
                            Column(horizontalAlignment = Alignment.End) {
                                if (sigBmp != null) {
                                    Image(
                                        bitmap = sigBmp.asImageBitmap(),
                                        contentDescription = "Signature",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(width = 76.dp, height = 24.dp)
                                    )
                                }
                                Text(
                                    business.signatureLabel.ifBlank { "Authorized Signatory" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
