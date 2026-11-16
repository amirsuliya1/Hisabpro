package com.example.ui.screens.operations

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.BarcodeUtils
import com.example.core.EscPosPrinterManager
import com.example.core.PairedPrinterDevice
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.local.entities.ThermalPaperWidth
import com.example.data.local.entities.UpiConfiguration
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun PrinterSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val configs by app.container.engagementRepository.printerConfigs.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val active = configs.firstOrNull { it.isDefault } ?: configs.firstOrNull() ?: PrinterConfiguration()

    var pairedDevices by remember { mutableStateOf<List<PairedPrinterDevice>>(emptyList()) }
    var printerName by remember(active.id) { mutableStateOf(active.printerName) }
    var macAddress by remember(active.id) { mutableStateOf(active.macAddress) }
    var width by remember(active.id) { mutableStateOf(active.paperWidth) }
    var showGstin by remember(active.id) { mutableStateOf(active.showGstin) }
    var showTax by remember(active.id) { mutableStateOf(active.showTax) }
    var showQr by remember(active.id) { mutableStateOf(active.showPaymentQr) }
    var footer by remember(active.id) { mutableStateOf(active.footerMessage) }

    fun scanDevices() {
        EscPosPrinterManager.listPairedDevices(context)
            .onSuccess { pairedDevices = it; if (it.isEmpty()) scope.launch { snackbar.showSnackbar("No paired Bluetooth printers found") } }
            .onFailure { scope.launch { snackbar.showSnackbar(it.message ?: "Bluetooth scan failed") } }
    }

    val btPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanDevices()
        else scope.launch { snackbar.showSnackbar("Bluetooth permission required to scan paired thermal printers") }
    }

    val currentConfig = active.copy(
        printerName = printerName.ifBlank { "Thermal Printer" },
        macAddress = macAddress.trim(),
        paperWidth = width,
        showGstin = showGstin,
        showTax = showTax,
        showPaymentQr = showQr,
        footerMessage = footer
    )

    val previewText = remember(currentConfig, profile) {
        EscPosPrinterManager.buildInvoiceReceiptText(
            profile = profile,
            invoice = Invoice(
                invoiceNumber = "INV-1001",
                documentType = InvoiceDocumentType.TAX_INVOICE,
                status = InvoiceStatus.PAID,
                customerName = "Sample Preview Party",
                subtotalPaise = 1000_00L,
                cgstPaise = 90_00L,
                sgstPaise = 90_00L,
                totalTaxPaise = 180_00L,
                grandTotalPaise = 1180_00L,
                paidAmountPaise = 1180_00L
            ),
            items = listOf(
                InvoiceItem(
                    invoiceId = 0L,
                    productName = "Sample Item",
                    unitName = "PCS",
                    quantityScaled = 200L,
                    ratePaise = 500_00L,
                    taxableAmountPaise = 1000_00L,
                    lineTotalPaise = 1180_00L
                )
            ),
            config = currentConfig
        )
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Thermal Printer & ESC/POS",
                subtitle = "58mm / 80mm Bluetooth receipts & UPI QR",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Bluetooth Printer Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !EscPosPrinterManager.hasBluetoothConnectPermission(context)) {
                                        btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    } else {
                                        scanDevices()
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("scan_bt_printers_btn")
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                                Spacer(Modifier.padding(3.dp))
                                Text("Scan Paired Devices")
                            }
                        }
                        pairedDevices.forEach { dev ->
                            OutlinedButton(
                                onClick = {
                                    printerName = dev.name
                                    macAddress = dev.macAddress
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${dev.name} (${dev.macAddress})")
                            }
                        }
                        OutlinedTextField(value = printerName, onValueChange = { printerName = it }, label = { Text("Printer Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = macAddress, onValueChange = { macAddress = it }, label = { Text("MAC Address (e.g. 00:11:22:33:44:55)") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThermalPaperWidth.entries.forEach { w ->
                                FilterChip(selected = width == w, onClick = { width = w }, label = { Text(w.label) })
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Show GSTIN on Receipt")
                            Switch(checked = showGstin, onCheckedChange = { showGstin = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Show Tax Breakdown")
                            Switch(checked = showTax, onCheckedChange = { showTax = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Print UPI Payment QR")
                            Switch(checked = showQr, onCheckedChange = { showQr = it })
                        }
                        OutlinedTextField(value = footer, onValueChange = { footer = it }, label = { Text("Receipt Footer") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.container.engagementRepository.savePrinterConfig(currentConfig.copy(isDefault = true))
                                        snackbar.showSnackbar("Printer configuration saved")
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("save_printer_btn")
                            ) { Text("Save Config") }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val bytes = EscPosPrinterManager.buildEscPosBytes(previewText)
                                        EscPosPrinterManager.sendToBluetoothPrinter(context, macAddress, bytes)
                                            .onSuccess { snackbar.showSnackbar("Test receipt printed") }
                                            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to print") }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Spacer(Modifier.padding(3.dp))
                                Text("Test Print")
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live ESC/POS Receipt Preview (${width.charsPerLine} cols)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = previewText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpiSettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val configs by app.container.engagementRepository.upiConfigs.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "UPI QR & Payment IDs",
                subtitle = "Print UPI QR on invoices & thermal bills",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_upi_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add UPI ID")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (configs.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyStateCard(
                    title = "No Saved UPI Configurations",
                    description = if (profile?.upiId.isNullOrBlank()) "Tap + to add your business UPI ID for automatic payment QR codes on invoices."
                    else "Active Profile UPI: ${profile!!.upiId}. Add additional UPI accounts below.",
                    actionLabel = "Add UPI ID",
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(configs, key = { it.id }) { cfg ->
                    val qrBmp = remember(cfg.upiId, cfg.payeeName) {
                        BarcodeUtils.generateQrBitmap(
                            BarcodeUtils.buildUpiQrPayload(cfg.upiId, cfg.payeeName, BigDecimal.ZERO, "Payment"),
                            220
                        )
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (qrBmp != null) {
                                Image(bitmap = qrBmp.asImageBitmap(), contentDescription = "UPI QR ${cfg.upiId}", modifier = Modifier.size(72.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(cfg.upiId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (cfg.isDefault) StatusPill("Default", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Text(cfg.payeeName, style = MaterialTheme.typography.bodyMedium)
                                Text(cfg.paymentInstructions, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (!cfg.isDefault) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            app.container.engagementRepository.saveUpiConfig(cfg.copy(isDefault = true))
                                            snackbar.showSnackbar("Set ${cfg.upiId} as default")
                                        }
                                    }) { Text("Set Default") }
                                }
                                IconButton(onClick = {
                                    scope.launch { app.container.engagementRepository.deleteUpiConfig(cfg) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete UPI ID")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var upiId by remember { mutableStateOf("") }
        var payee by remember { mutableStateOf(profile?.businessName ?: "") }
        var instr by remember { mutableStateOf("Scan via any UPI app and share reference ID.") }
        var isDefault by remember { mutableStateOf(configs.isEmpty()) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add UPI Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("UPI ID (e.g. shop@okaxis) *") }, singleLine = true)
                    OutlinedTextField(value = payee, onValueChange = { payee = it }, label = { Text("Payee Name") }, singleLine = true)
                    OutlinedTextField(value = instr, onValueChange = { instr = it }, label = { Text("Payment Note") })
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Use as Default Invoice QR")
                        Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = upiId.contains("@"),
                    onClick = {
                        scope.launch {
                            app.container.engagementRepository.saveUpiConfig(
                                UpiConfiguration(
                                    upiId = upiId.trim(),
                                    payeeName = payee.ifBlank { profile?.businessName ?: "Merchant" },
                                    paymentInstructions = instr,
                                    isDefault = isDefault
                                )
                            )
                            showAddDialog = false
                            snackbar.showSnackbar("UPI configuration saved")
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}
