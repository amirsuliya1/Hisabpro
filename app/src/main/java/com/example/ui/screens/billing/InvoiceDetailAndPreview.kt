package com.example.ui.screens.billing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.R
import com.example.core.BarcodeUtils
import com.example.core.DateUtils
import com.example.core.EscPosPrinterManager
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.InvoiceTemplate
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.SaveInvoiceRequest
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.StatusPill
import com.example.ui.screens.settings.studio.CorporateWaveInvoiceCard
import com.example.ui.screens.settings.studio.rememberDecodedAssetBitmap
import com.example.ui.screens.settings.studio.toFullAddressString
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InvoiceDetailScreen(
    invoiceId: Long,
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onEditDraft: (Long, InvoiceDocumentType) -> Unit,
    onOpenNewInvoice: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val invoice by app.container.billingRepository.observeInvoice(invoiceId).collectAsStateWithLifecycle(null)
    val items by app.container.billingRepository.observeInvoiceItems(invoiceId).collectAsStateWithLifecycle(emptyList())
    val auditLogs by app.container.billingRepository.observeInvoiceAuditLogs(invoiceId).collectAsStateWithLifecycle(emptyList())
    val printers by app.container.engagementRepository.printerConfigs.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val selectedTemplate by app.container.businessRepository.selectedTemplate.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false
    val semantic = LocalSemanticColors.current

    var showPayDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("Customer request") }
    var thermalPreviewText by remember { mutableStateOf<String?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = invoice?.invoiceNumber ?: "Invoice Details",
                subtitle = invoice?.documentType?.label,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val inv = invoice
        if (inv == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Loading invoice...")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(inv.customerName, style = MaterialTheme.typography.titleLarge)
                                Text(DateUtils.formatDateTime(inv.invoiceDateMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                label = inv.status.label,
                                containerColor = if (inv.status == InvoiceStatus.PAID) semantic.successContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (inv.status == InvoiceStatus.PAID) semantic.success else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Grand Total", style = MaterialTheme.typography.labelSmall)
                                MoneyText(inv.grandTotalPaise, currency, hide, fontSizeSp = 20)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Balance Due", style = MaterialTheme.typography.labelSmall)
                                MoneyText(inv.balanceDuePaise, currency, hide, color = if (inv.balanceDuePaise > 0L) semantic.danger else semantic.success, fontSizeSp = 20)
                            }
                        }
                        if (inv.loyaltyPointsEarned > 0L || inv.loyaltyPointsRedeemed > 0L) {
                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (inv.loyaltyPointsEarned > 0L) {
                                    Text("Loyalty Earned: +${inv.loyaltyPointsEarned} pts", style = MaterialTheme.typography.bodySmall, color = semantic.success)
                                }
                                if (inv.loyaltyPointsRedeemed > 0L) {
                                    Text("Redeemed: ${inv.loyaltyPointsRedeemed} pts (${Money.formatPaise(inv.loyaltyDiscountPaise, currency, hide)})", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenPreview(inv.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Preview / PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isExportingPdf) return@OutlinedButton
                            isExportingPdf = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        PdfGenerator.generateInvoicePdf(context, profile, inv, items, selectedTemplate)
                                    }
                                }.onSuccess { pdf ->
                                    isExportingPdf = false
                                    PdfGenerator.shareFile(context, pdf, "application/pdf", "Share ${inv.invoiceNumber}")
                                }.onFailure { err ->
                                    isExportingPdf = false
                                    snackbar.showSnackbar(err.message ?: "PDF export failed")
                                }
                            }
                        },
                        enabled = !isExportingPdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Share, null)
                        }
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text(if (isExportingPdf) "Building..." else "Share PDF")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val activePrinter = printers.firstOrNull { it.isDefault } ?: printers.firstOrNull() ?: PrinterConfiguration()
                        val receiptText = EscPosPrinterManager.buildInvoiceReceiptText(profile, inv, items, activePrinter)
                        if (activePrinter.macAddress.isNotBlank()) {
                            scope.launch {
                                val qrPayload = if (activePrinter.showPaymentQr && !profile?.upiId.isNullOrBlank() && inv.balanceDuePaise > 0L) {
                                    BarcodeUtils.buildUpiQrPayload(
                                        profile!!.upiId,
                                        profile!!.businessName,
                                        Money.fromPaise(inv.balanceDuePaise),
                                        inv.invoiceNumber
                                    )
                                } else null
                                val bytes = EscPosPrinterManager.buildEscPosBytes(receiptText, qrPayload)
                                EscPosPrinterManager.sendToBluetoothPrinter(context, activePrinter.macAddress, bytes)
                                    .onSuccess {
                                        app.container.billingRepository.logThermalPrint(inv.id, activePrinter.printerName)
                                        snackbar.showSnackbar("Printed receipt on ${activePrinter.printerName}")
                                    }
                                    .onFailure {
                                        thermalPreviewText = receiptText
                                        snackbar.showSnackbar(it.message ?: "Thermal printer offline — showing receipt preview")
                                    }
                            }
                        } else {
                            thermalPreviewText = receiptText
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Receipt, null)
                    Spacer(modifier = Modifier.padding(3.dp))
                    Text("Thermal Print (ESC/POS)")
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inv.status == InvoiceStatus.DRAFT) {
                        Button(onClick = { onEditDraft(inv.id, inv.documentType) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(modifier = Modifier.padding(3.dp))
                            Text("Edit Draft")
                        }
                    } else {
                        if (inv.balanceDuePaise > 0L && inv.status != InvoiceStatus.CANCELLED && inv.customerId != null) {
                            Button(onClick = { showPayDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Payments, null)
                                Spacer(modifier = Modifier.padding(3.dp))
                                Text("Record Payment")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.container.billingRepository.duplicateAsDraft(inv.id)
                                        .onSuccess { newId -> onOpenNewInvoice(newId) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(modifier = Modifier.padding(3.dp))
                            Text("Duplicate")
                        }
                    }
                }
            }

            if (inv.status != InvoiceStatus.DRAFT && inv.status != InvoiceStatus.CANCELLED && inv.documentType != InvoiceDocumentType.SALES_RETURN) {
                item {
                    var isProcessingReturn by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !isProcessingReturn,
                            onClick = {
                                if (isProcessingReturn) return@OutlinedButton
                                isProcessingReturn = true
                                scope.launch {
                                    val retReq = SaveInvoiceRequest(
                                        documentType = InvoiceDocumentType.SALES_RETURN,
                                        customerId = inv.customerId,
                                        customerName = inv.customerName,
                                        customerPhone = inv.customerPhone,
                                        customerAddress = inv.customerAddress,
                                        customerGstin = inv.customerGstin,
                                        isInterState = inv.isInterState,
                                        invoiceDiscountFlat = Money.fromPaise(inv.invoiceDiscountFlatPaise),
                                        items = items.map {
                                            InvoiceLineDraft(
                                                productId = it.productId,
                                                batchId = it.batchId,
                                                productName = it.productName,
                                                productCode = it.productCode,
                                                hsnSacCode = it.hsnSacCode,
                                                unitName = it.unitName,
                                                quantity = Money.fromPaise(it.quantityScaled),
                                                rate = Money.fromPaise(it.ratePaise),
                                                purchaseCost = Money.fromPaise(it.purchaseCostPaise),
                                                discountPercent = Money.fromPaise(it.discountPercentScaled),
                                                discountFlat = Money.fromPaise(it.discountAmountPaise),
                                                taxRatePercent = Money.fromPaise(it.taxRatePercentScaled),
                                                isTaxInclusive = it.isTaxInclusive,
                                                batchNumber = it.batchNumber,
                                                expiryDateMillis = it.expiryDateMillis,
                                                serialNumber = it.serialNumber
                                            )
                                        },
                                        linkedOriginalInvoiceId = inv.id,
                                        notes = "Sales return against ${inv.invoiceNumber}"
                                    )
                                    app.container.billingRepository.saveOrFinalizeInvoice(retReq)
                                        .onSuccess { retId ->
                                            isProcessingReturn = false
                                            snackbar.showSnackbar("Sales return generated")
                                            onOpenNewInvoice(retId)
                                        }
                                        .onFailure {
                                            isProcessingReturn = false
                                            snackbar.showSnackbar(it.message ?: "Error generating return")
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AssignmentReturn, null)
                            Spacer(modifier = Modifier.padding(3.dp))
                            Text(if (isProcessingReturn) "Saving..." else "Sales Return")
                        }
                        OutlinedButton(
                            enabled = !isProcessingReturn,
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.padding(3.dp))
                            Text("Cancel & Reverse", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Text("Items (${items.size})", style = MaterialTheme.typography.titleMedium) }
            items(items, key = { "line_${it.id}" }) { line ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName, style = MaterialTheme.typography.titleMedium)
                            Text("${Money.formatQty(Money.fromPaise(line.quantityScaled))} ${line.unitName} x ${Money.formatPaise(line.ratePaise, currency, hide)}", style = MaterialTheme.typography.bodyMedium)
                            if (line.batchNumber.isNotBlank()) {
                                Text("Batch: ${line.batchNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        MoneyText(line.lineTotalPaise, currency, hide)
                    }
                }
            }

            if (auditLogs.isNotEmpty()) {
                item { Text("Invoice Audit Trail (${auditLogs.size})", style = MaterialTheme.typography.titleMedium) }
                items(auditLogs, key = { "audit_${it.id}" }) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(DateUtils.formatDateTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(log.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showPayDialog && invoice?.customerId != null) {
        val inv = invoice!!
        var amt by remember { mutableStateOf(Money.formatPlain(Money.fromPaise(inv.balanceDuePaise))) }
        var mode by remember { mutableStateOf(PaymentMode.CASH) }
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Receive Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amt,
                        onValueChange = { amt = it },
                        label = { Text("Amount ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK_TRANSFER).forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val paise = Money.safeToPaise(Money.of(amt))
                        app.container.accountingRepository.recordCustomerPayment(
                            customerId = inv.customerId!!,
                            amountPaise = paise,
                            paymentMode = mode,
                            invoiceIdToAllocate = inv.id,
                            notes = "Payment for ${inv.invoiceNumber}"
                        ).onSuccess {
                            showPayDialog = false
                            snackbar.showSnackbar("Payment recorded")
                        }.onFailure {
                            snackbar.showSnackbar(it.message ?: "Error recording payment")
                        }
                    }
                }) { Text("Save Payment") }
            },
            dismissButton = { OutlinedButton(onClick = { showPayDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCancelDialog && invoice != null) {
        ConfirmActionDialog(
            title = "Cancel & Reverse Invoice?",
            message = "This will keep ${invoice!!.invoiceNumber} in your audit history as Cancelled, restore sold inventory, and post a reversing accounting journal entry.",
            confirmLabel = "Cancel Invoice",
            onConfirm = {
                scope.launch {
                    app.container.billingRepository.cancelFinalizedInvoice(invoice!!.id, cancelReason)
                        .onSuccess {
                            showCancelDialog = false
                            snackbar.showSnackbar("Invoice cancelled with reversal entries")
                        }
                        .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                }
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    if (thermalPreviewText != null) {
        AlertDialog(
            onDismissRequest = { thermalPreviewText = null },
            title = { Text("Thermal Receipt Preview") },
            text = {
                Text(
                    thermalPreviewText!!,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { thermalPreviewText = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun InvoicePreviewScreen(
    invoiceId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val invoice by app.container.billingRepository.observeInvoice(invoiceId).collectAsStateWithLifecycle(null)
    val items by app.container.billingRepository.observeInvoiceItems(invoiceId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val templates by app.container.businessRepository.invoiceTemplates.collectAsStateWithLifecycle(emptyList())
    val selectedTemplate by app.container.businessRepository.selectedTemplate.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    var isExportingPdf by remember { mutableStateOf(false) }

    val activeTpl = selectedTemplate ?: templates.firstOrNull { it.isSelected } ?: templates.firstOrNull()
        ?: InvoiceTemplate(code = "CORPORATE_WAVE", name = "Corporate Wave Navy", description = "")

    Scaffold(
        topBar = {
            HisabPulseTopBar(title = "Invoice Preview", subtitle = activeTpl.name, onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val inv = invoice ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "template_switcher") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { tpl ->
                        FilterChip(
                            selected = tpl.id == activeTpl.id,
                            onClick = { scope.launch { app.container.businessRepository.selectInvoiceTemplate(tpl.id) } },
                            label = { Text(tpl.name) }
                        )
                    }
                }
            }
            item(key = "invoice_card_preview") {
                if (activeTpl.code.startsWith("CORPORATE_WAVE")) {
                    CorporateWaveInvoiceCard(
                        template = activeTpl,
                        businessName = profile?.businessName.orEmpty(),
                        tagline = profile?.tagline.orEmpty(),
                        address = profile.toFullAddressString(),
                        phone = profile?.phone.orEmpty(),
                        email = profile?.email.orEmpty(),
                        website = profile?.website.orEmpty(),
                        gstin = profile?.gstin.orEmpty(),
                        upiId = profile?.upiId.orEmpty(),
                        signatureLabel = profile?.signatureLabel ?: "Authorized Signature",
                        logoUri = profile?.logoUri.orEmpty(),
                        signatureUri = profile?.signatureUri.orEmpty(),
                        sealUri = profile?.sealUri.orEmpty(),
                        currencySymbol = currency,
                        invoice = inv,
                        items = items
                    )
                } else {
                    val accentColor = remember(activeTpl.accentColorHex) {
                        runCatching {
                            Color(android.graphics.Color.parseColor(activeTpl.accentColorHex))
                        }.getOrNull()
                    } ?: MaterialTheme.colorScheme.primary
                    val logoBmp = rememberDecodedAssetBitmap(profile?.logoUri, 160, 160)
                    val sigBmp = rememberDecodedAssetBitmap(profile?.signatureUri, 260, 100)

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(accentColor)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val align = when (activeTpl.logoPosition) {
                                    "CENTER" -> Alignment.CenterHorizontally
                                    "RIGHT" -> Alignment.End
                                    else -> Alignment.Start
                                }
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                                    if (activeTpl.showLogo) {
                                        if (logoBmp != null) {
                                            Image(
                                                bitmap = logoBmp.asImageBitmap(),
                                                contentDescription = "Business Logo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_app_logo),
                                                contentDescription = "Default Logo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    if (activeTpl.showBusinessName) {
                                        Text(profile?.businessName ?: "HisabPulse Business", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                    if (!profile?.tagline.isNullOrBlank()) {
                                        Text(profile!!.tagline, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (activeTpl.showAddress && !profile?.address.isNullOrBlank()) {
                                    Text(profile.toFullAddressString(), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                                }
                                if (activeTpl.showGstin && !profile?.gstin.isNullOrBlank()) {
                                    Text("GSTIN: ${profile!!.gstin}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${inv.documentType.label} • #${inv.invoiceNumber}", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Date: ${DateUtils.formatDate(inv.invoiceDateMillis)}", color = Color.White)
                                }
                            }

                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (activeTpl.headerText.isNotBlank()) {
                                    Text(activeTpl.headerText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    HorizontalDivider()
                                }
                                Text("Bill To: ${inv.customerName}", fontWeight = FontWeight.Bold)
                                if (inv.customerPhone.isNotBlank()) {
                                    Text("Phone: ${inv.customerPhone}", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (activeTpl.showGstin && inv.customerGstin.isNotBlank()) {
                                    Text("Party GSTIN: ${inv.customerGstin}", style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider()

                                items.forEach { item ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.productName, fontWeight = FontWeight.Medium)
                                            Text("${Money.formatQty(Money.fromPaise(item.quantityScaled))} ${item.unitName} x ${Money.formatPaise(item.ratePaise, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(Money.formatPaise(item.lineTotalPaise, currency), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                HorizontalDivider()

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Subtotal")
                                    Text(Money.formatPaise(inv.subtotalPaise, currency))
                                }
                                if (activeTpl.showTaxBreakdown && inv.totalTaxPaise > 0L) {
                                    if (inv.cgstPaise > 0L) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("CGST", style = MaterialTheme.typography.bodySmall)
                                            Text(Money.formatPaise(inv.cgstPaise, currency), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (inv.sgstPaise > 0L) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("SGST", style = MaterialTheme.typography.bodySmall)
                                            Text(Money.formatPaise(inv.sgstPaise, currency), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (inv.igstPaise > 0L) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("IGST", style = MaterialTheme.typography.bodySmall)
                                            Text(Money.formatPaise(inv.igstPaise, currency), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                if (inv.roundOffPaise != 0L) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Round Off", style = MaterialTheme.typography.bodySmall)
                                        Text(Money.formatPaise(inv.roundOffPaise, currency), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Grand Total", fontWeight = FontWeight.Bold)
                                    Text(Money.formatPaise(inv.grandTotalPaise, currency), fontWeight = FontWeight.Bold, color = accentColor)
                                }
                                if (activeTpl.showPaymentInfo) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Paid Amount", style = MaterialTheme.typography.bodySmall)
                                        Text(Money.formatPaise(inv.paidAmountPaise, currency), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Balance Due", fontWeight = FontWeight.Bold)
                                        Text(Money.formatPaise(inv.balanceDuePaise, currency), fontWeight = FontWeight.Bold, color = if (inv.balanceDuePaise > 0L) MaterialTheme.colorScheme.error else accentColor)
                                    }
                                }

                                if (activeTpl.showUpiQr && !profile?.upiId.isNullOrBlank() && inv.balanceDuePaise > 0L) {
                                    val qr by produceState<android.graphics.Bitmap?>(initialValue = null, inv.id, profile?.upiId) {
                                        value = withContext(Dispatchers.Default) {
                                            BarcodeUtils.generateQrBitmap(
                                                BarcodeUtils.buildUpiQrPayload(
                                                    profile!!.upiId,
                                                    profile!!.businessName,
                                                    Money.fromPaise(inv.balanceDuePaise),
                                                    inv.invoiceNumber
                                                ),
                                                260
                                            )
                                        }
                                    }
                                    if (qr != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(bitmap = qr!!.asImageBitmap(), contentDescription = "UPI Payment QR", modifier = Modifier.size(100.dp))
                                        Text("Scan UPI to pay balance", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                val terms = activeTpl.termsText.ifBlank { inv.termsAndConditions }
                                if (terms.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Terms: $terms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (activeTpl.showSignature) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                        if (sigBmp != null) {
                                            Image(
                                                bitmap = sigBmp.asImageBitmap(),
                                                contentDescription = "Authorized Signature",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(width = 110.dp, height = 34.dp)
                                            )
                                        }
                                        Text(profile?.signatureLabel ?: "Authorized Signatory", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (activeTpl.footerMessage.isNotBlank()) {
                                    Text(activeTpl.footerMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "preview_actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (isExportingPdf) return@Button
                            isExportingPdf = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        PdfGenerator.generateInvoicePdf(context, profile, inv, items, activeTpl)
                                    }
                                }.onSuccess { file ->
                                    isExportingPdf = false
                                    PdfGenerator.shareFile(context, file, "application/pdf", "Share Invoice PDF")
                                }.onFailure { err ->
                                    isExportingPdf = false
                                    snackbar.showSnackbar(err.message ?: "Failed to export PDF")
                                }
                            }
                        },
                        enabled = !isExportingPdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Share, null)
                        }
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text(if (isExportingPdf) "Building..." else "Share PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isExportingPdf) return@OutlinedButton
                            isExportingPdf = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        PdfGenerator.generateInvoicePdf(context, profile, inv, items, activeTpl)
                                    }
                                }.onSuccess { file ->
                                    isExportingPdf = false
                                    PdfGenerator.printPdfFile(context, file, inv.invoiceNumber)
                                }.onFailure { err ->
                                    isExportingPdf = false
                                    snackbar.showSnackbar(err.message ?: "Failed to print PDF")
                                }
                            }
                        },
                        enabled = !isExportingPdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Print")
                    }
                }
            }
        }
    }
}
