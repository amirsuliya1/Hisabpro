package com.example.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.EscPosPrinterManager
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.local.entities.DailyClosing
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.repository.DailyClosingPreview
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DailyClosingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val closings by app.container.engagementRepository.dailyClosings.collectAsStateWithLifecycle(emptyList())
    val printers by app.container.engagementRepository.printerConfigs.collectAsStateWithLifecycle(emptyList())
    val currency = profile?.currencySymbol ?: "₹"

    var preview by remember { mutableStateOf<DailyClosingPreview?>(null) }
    var countedCashText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun reloadPreview() {
        scope.launch {
            val p = app.container.engagementRepository.buildDailyClosingPreview()
            preview = p
            if (countedCashText.isBlank()) {
                countedCashText = Money.formatPlain(Money.fromPaise(p.expectedClosingCashPaise))
            }
        }
    }

    LaunchedEffect(Unit) { reloadPreview() }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Daily Closing & Reconciliation",
                subtitle = preview?.closingDateIso ?: "Cash register summary",
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
            val p = preview
            if (p != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Cash Drawer Calculation (${p.closingDateIso})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                StatusPill(
                                    label = if (p.alreadyClosed) "Closed" else "Open",
                                    containerColor = if (p.alreadyClosed) semantic.successContainer else semantic.warningContainer,
                                    contentColor = if (p.alreadyClosed) semantic.success else semantic.warning
                                )
                            }
                            ClosingRow("Opening Cash", p.openingCashPaise, currency)
                            ClosingRow("(+) Cash Sales", p.cashSalesPaise, currency)
                            ClosingRow("(+) Cash Customer Receipts", p.cashReceiptsPaise, currency)
                            ClosingRow("(-) Cash Expenses", -p.cashExpensesPaise, currency)
                            ClosingRow("(-) Cash Supplier Payments", -p.cashPaymentsPaise, currency)
                            ClosingRow("(-) Cash Refunds", -p.cashRefundsPaise, currency)
                            HorizontalDivider()
                            ClosingRow("Expected Closing Cash", p.expectedClosingCashPaise, currency, bold = true)
                            HorizontalDivider()
                            ClosingRow("UPI Collections", p.upiCollectionsPaise, currency)
                            ClosingRow("Bank / Card Collections", p.bankCollectionsPaise, currency)
                            ClosingRow("Credit Sales (Pending)", p.creditSalesPaise, currency)
                            ClosingRow("Total Day Sales", p.totalSalesPaise, currency, bold = true)
                        }
                    }
                }

                if (!p.alreadyClosed) {
                    val countedPaise = Money.safeToPaise(Money.of(countedCashText))
                    val diff = countedPaise - p.expectedClosingCashPaise
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Physical Cash Count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = countedCashText,
                                    onValueChange = { countedCashText = it },
                                    label = { Text("Actual Counted Cash ($currency)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("counted_cash_input")
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (diff == 0L) "Cash Balanced" else if (diff > 0L) "Excess Cash" else "Cash Shortage", fontWeight = FontWeight.SemiBold)
                                    MoneyText(
                                        paise = diff,
                                        currencySymbol = currency,
                                        color = when {
                                            diff == 0L -> semantic.success
                                            diff > 0L -> semantic.warning
                                            else -> semantic.danger
                                        }
                                    )
                                }
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Closing Notes / Reason for Variance") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            app.container.engagementRepository.submitDailyClosing(
                                                preview = p,
                                                actualCountedCashPaise = countedPaise,
                                                notes = notes
                                            ).onSuccess {
                                                reloadPreview()
                                                snackbar.showSnackbar("Daily closing locked for ${p.closingDateIso}")
                                            }.onFailure {
                                                snackbar.showSnackbar(it.message ?: "Failed to close day")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("finalize_daily_closing_btn")
                                ) {
                                    Icon(Icons.Default.LockClock, contentDescription = null)
                                    Spacer(Modifier.padding(4.dp))
                                    Text("Finalize Daily Closing")
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Past Daily Closings (${closings.size})", style = MaterialTheme.typography.titleMedium) }
            if (closings.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Closed Days Yet",
                        description = "Submit today's reconciliation above to lock opening/closing cash balances."
                    )
                }
            } else {
                items(closings, key = { it.id }) { c: DailyClosing ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.closingDateIso, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Expected ${Money.formatPaise(c.expectedClosingCashPaise, currency)} • Counted ${Money.formatPaise(c.actualCountedCashPaise, currency)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Diff: ${Money.formatPaise(c.cashDifferencePaise, currency)} • Sales: ${Money.formatPaise(c.totalSalesPaise, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    scope.launch {
                                        val pdf = withContext(Dispatchers.IO) {
                                            PdfGenerator.generateDailyClosingPdf(context, profile, c)
                                        }
                                        PdfGenerator.shareFile(context, pdf, "application/pdf", "Share Daily Closing ${c.closingDateIso}")
                                    }
                                }) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Share Daily Closing PDF")
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val cfg = printers.firstOrNull { it.isDefault } ?: PrinterConfiguration()
                                        val txt = EscPosPrinterManager.buildDailyClosingReceiptText(profile, c, cfg.paperWidth)
                                        if (cfg.macAddress.isBlank()) {
                                            snackbar.showSnackbar("Configure Bluetooth thermal printer in More → Thermal Printer first")
                                        } else {
                                            EscPosPrinterManager.sendToBluetoothPrinter(
                                                context,
                                                cfg.macAddress,
                                                EscPosPrinterManager.buildEscPosBytes(txt)
                                            ).onSuccess { snackbar.showSnackbar("Sent to thermal printer") }
                                                .onFailure { snackbar.showSnackbar(it.message ?: "Print failed") }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Print, contentDescription = "Thermal Print Closing")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosingRow(label: String, paise: Long, currency: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        MoneyText(paise = paise, currencySymbol = currency, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
