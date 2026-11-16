package com.example.ui.screens.settings.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entities.InvoicePaperSize
import com.example.data.local.entities.InvoiceTemplate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioStyleTab(
    template: InvoiceTemplate,
    customHexInput: String,
    secondaryHexInput: String,
    onCustomHexChange: (String) -> Unit,
    onSecondaryHexChange: (String) -> Unit,
    onTemplateChange: (InvoiceTemplate) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Curated Corporate Color Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                STUDIO_COLOR_SCHEMES.forEach { scheme ->
                    val primary = parseColorSafe(scheme.primaryHex)
                    val secondary = parseColorSafe(scheme.secondaryHex)
                    val chosen = template.accentColorHex.equals(scheme.primaryHex, ignoreCase = true) &&
                        template.secondaryColorHex.equals(scheme.secondaryHex, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onCustomHexChange(scheme.primaryHex)
                                onSecondaryHexChange(scheme.secondaryHex)
                                onTemplateChange(
                                    template.copy(
                                        accentColorHex = scheme.primaryHex,
                                        secondaryColorHex = scheme.secondaryHex
                                    )
                                )
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(primary)
                                .border(
                                    width = if (chosen) 3.dp else 1.dp,
                                    color = if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(secondary)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                            if (chosen) {
                                Icon(Icons.Default.Check, contentDescription = scheme.name, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(scheme.name.substringBefore(" "), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider()
            Text("Custom Color Picker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Text("Primary Header & Border Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CUSTOM_SWATCH_COLORS.forEach { hex ->
                    val c = parseColorSafe(hex)
                    val active = template.accentColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (active) 2.5.dp else 1.dp, if (active) MaterialTheme.colorScheme.onSurface else Color.LightGray, CircleShape)
                            .clickable {
                                onCustomHexChange(hex)
                                onTemplateChange(template.copy(accentColorHex = hex))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) Icon(Icons.Default.Check, contentDescription = hex, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            OutlinedTextField(
                value = customHexInput,
                onValueChange = { raw ->
                    val cleaned = raw.uppercase().take(7)
                    onCustomHexChange(cleaned)
                    if (cleaned.matches(Regex("^#[0-9A-F]{6}$"))) {
                        onTemplateChange(template.copy(accentColorHex = cleaned))
                    }
                },
                label = { Text("Primary Hex Color (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Secondary Wave & Pill Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CUSTOM_SWATCH_COLORS.forEach { hex ->
                    val c = parseColorSafe(hex)
                    val active = template.secondaryColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (active) 2.5.dp else 1.dp, if (active) MaterialTheme.colorScheme.onSurface else Color.LightGray, CircleShape)
                            .clickable {
                                onSecondaryHexChange(hex)
                                onTemplateChange(template.copy(secondaryColorHex = hex))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) Icon(Icons.Default.Check, contentDescription = hex, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            OutlinedTextField(
                value = secondaryHexInput,
                onValueChange = { raw ->
                    val cleaned = raw.uppercase().take(7)
                    onSecondaryHexChange(cleaned)
                    if (cleaned.matches(Regex("^#[0-9A-F]{6}$"))) {
                        onTemplateChange(template.copy(secondaryColorHex = cleaned))
                    }
                },
                label = { Text("Secondary Wave Hex Color (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Paper Size Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvoicePaperSize.entries.forEach { ps ->
                    FilterChip(
                        selected = template.paperSize == ps,
                        onClick = { onTemplateChange(template.copy(paperSize = ps)) },
                        label = { Text(ps.label) }
                    )
                }
            }

            if (!template.code.startsWith("CORPORATE_WAVE")) {
                Text("Header & Logo Alignment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LEFT", "CENTER", "RIGHT").forEach { pos ->
                        FilterChip(
                            selected = template.logoPosition == pos,
                            onClick = { onTemplateChange(template.copy(logoPosition = pos)) },
                            label = { Text("Logo: $pos") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioBrandingTab(
    business: StudioEditableBusiness,
    onBusinessChange: (StudioEditableBusiness) -> Unit,
    onPickLogo: () -> Unit,
    onDrawSignature: () -> Unit,
    onPickSignatureImage: () -> Unit,
    onPickSealImage: () -> Unit,
    onSaveBusinessDetails: () -> Unit
) {
    val logoBmp = rememberDecodedAssetBitmap(business.logoUri, 200, 200)
    val sigBmp = rememberDecodedAssetBitmap(business.signatureUri, 360, 140)
    val sealBmp = rememberDecodedAssetBitmap(business.sealUri, 160, 160)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Shop Logo, Signature & Business Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Shop Logo Picker Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (logoBmp != null) {
                        Image(
                            bitmap = logoBmp.asImageBitmap(),
                            contentDescription = "Selected business logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "Default HisabPulse logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onPickLogo,
                        modifier = Modifier.fillMaxWidth().testTag("studio_pick_logo_btn")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (business.logoUri.isBlank()) "Upload Shop Logo" else "Change Shop Logo")
                    }
                    if (business.logoUri.isNotBlank()) {
                        TextButton(onClick = { onBusinessChange(business.copy(logoUri = "")) }) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset to Default Logo")
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Authorized Signature & Shop Seal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            if (sigBmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = sigBmp.asImageBitmap(),
                        contentDescription = "Saved authorized signature",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDrawSignature,
                    modifier = Modifier.weight(1f).testTag("studio_draw_signature_btn")
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Draw Sign")
                }
                OutlinedButton(
                    onClick = onPickSignatureImage,
                    modifier = Modifier.weight(1f).testTag("studio_upload_signature_btn")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Sign")
                }
            }
            if (business.signatureUri.isNotBlank()) {
                TextButton(onClick = { onBusinessChange(business.copy(signatureUri = "")) }) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove Saved Signature")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (sealBmp != null) {
                        Image(
                            bitmap = sealBmp.asImageBitmap(),
                            contentDescription = "Shop seal image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(42.dp)
                        )
                    } else {
                        Icon(Icons.Default.Verified, contentDescription = "Default Seal Badge", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                OutlinedButton(
                    onClick = onPickSealImage,
                    modifier = Modifier.weight(1f).testTag("studio_upload_seal_btn")
                ) {
                    Text(if (business.sealUri.isBlank()) "Upload Shop Seal / Stamp" else "Change Seal / Stamp")
                }
                if (business.sealUri.isNotBlank()) {
                    TextButton(onClick = { onBusinessChange(business.copy(sealUri = "")) }) {
                        Text("Clear")
                    }
                }
            }

            HorizontalDivider()
            OutlinedTextField(
                value = business.businessName,
                onValueChange = { onBusinessChange(business.copy(businessName = it)) },
                label = { Text("Shop / Business Name") },
                modifier = Modifier.fillMaxWidth().testTag("studio_biz_name_input")
            )
            OutlinedTextField(
                value = business.tagline,
                onValueChange = { onBusinessChange(business.copy(tagline = it)) },
                label = { Text("Business Tagline (e.g. Wholesale & Retail Hub)") },
                modifier = Modifier.fillMaxWidth().testTag("studio_tagline_input")
            )
            OutlinedTextField(
                value = business.address,
                onValueChange = { onBusinessChange(business.copy(address = it)) },
                label = { Text("Business Address") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = business.phone,
                onValueChange = { onBusinessChange(business.copy(phone = it)) },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = business.email,
                onValueChange = { onBusinessChange(business.copy(email = it)) },
                label = { Text("Email ID") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = business.website,
                onValueChange = { onBusinessChange(business.copy(website = it)) },
                label = { Text("Website (Optional, e.g. www.yourshop.com)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = business.gstin,
                onValueChange = { onBusinessChange(business.copy(gstin = it.uppercase())) },
                label = { Text("GSTIN (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = business.upiId,
                onValueChange = { onBusinessChange(business.copy(upiId = it)) },
                label = { Text("UPI ID for Invoice QR") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = business.signatureLabel,
                onValueChange = { onBusinessChange(business.copy(signatureLabel = it)) },
                label = { Text("Authorized Signature Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSaveBusinessDetails,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("studio_save_biz_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Business Details")
            }
        }
    }
}

@Composable
fun StudioSectionsTab(
    template: InvoiceTemplate,
    onTemplateChange: (InvoiceTemplate) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Visible Invoice Blocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StudioToggleRow("Show Business Logo", template.showLogo) { onTemplateChange(template.copy(showLogo = it)) }
            StudioToggleRow("Show Business Name", template.showBusinessName) { onTemplateChange(template.copy(showBusinessName = it)) }
            StudioToggleRow("Show Address & Contact", template.showAddress) { onTemplateChange(template.copy(showAddress = it)) }
            StudioToggleRow("Show GSTIN", template.showGstin) { onTemplateChange(template.copy(showGstin = it)) }
            StudioToggleRow("Show HSN / SAC Code", template.showHsn) { onTemplateChange(template.copy(showHsn = it)) }
            StudioToggleRow("Show Discount Row / Column", template.showDiscountColumn) { onTemplateChange(template.copy(showDiscountColumn = it)) }
            StudioToggleRow("Show Tax Breakdown (CGST/SGST/IGST)", template.showTaxBreakdown) { onTemplateChange(template.copy(showTaxBreakdown = it)) }
            StudioToggleRow("Show Payment Details & Balance", template.showPaymentInfo) { onTemplateChange(template.copy(showPaymentInfo = it)) }
            StudioToggleRow("Show UPI Payment QR", template.showUpiQr) { onTemplateChange(template.copy(showUpiQr = it)) }
            StudioToggleRow("Show Customer Notes", template.showCustomerNotes) { onTemplateChange(template.copy(showCustomerNotes = it)) }
            StudioToggleRow("Show Signature & Seal Cards", template.showSignature) { onTemplateChange(template.copy(showSignature = it)) }
        }
    }
}

@Composable
fun StudioCopyAndSaveTab(
    template: InvoiceTemplate,
    isCustomTemplate: Boolean,
    isExportingPdf: Boolean = false,
    onTemplateChange: (InvoiceTemplate) -> Unit,
    onSaveAndActivate: () -> Unit,
    onOpenSaveAsNew: () -> Unit,
    onPreviewSamplePdf: () -> Unit,
    onDeleteCustom: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Header, Terms & Save Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = template.headerText,
                onValueChange = { onTemplateChange(template.copy(headerText = it)) },
                label = { Text("Top Banner / Fallback Tagline") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = template.termsText,
                onValueChange = { onTemplateChange(template.copy(termsText = it)) },
                label = { Text("Terms & Conditions (Line by Line)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = template.footerMessage,
                onValueChange = { onTemplateChange(template.copy(footerMessage = it)) },
                label = { Text("Bottom Closing Message (e.g. Thank You For Your Business!)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onSaveAndActivate,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_template_design_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Set Active Template")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenSaveAsNew,
                    modifier = Modifier.weight(1f).testTag("save_as_new_template_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save As New")
                }
                OutlinedButton(
                    onClick = onPreviewSamplePdf,
                    enabled = !isExportingPdf,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExportingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isExportingPdf) "Building..." else "Sample PDF")
                }
            }

            if (isCustomTemplate) {
                TextButton(
                    onClick = onDeleteCustom,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Custom Template", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StudioToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
