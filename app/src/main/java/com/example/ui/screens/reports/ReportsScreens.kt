package com.example.ui.screens.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.R
import com.example.core.DateRangeFilter
import com.example.core.DateUtils
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PurchaseDocumentType
import com.example.data.repository.InventoryRepository
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GstReportsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val invoices by app.container.billingRepository.allInvoices.collectAsStateWithLifecycle(emptyList())
    val purchases by app.container.billingRepository.allPurchases.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var filter by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    val (start, end) = remember(filter, customStart, customEnd) {
        DateUtils.resolveRange(filter, customStart, customEnd)
    }

    val gstSales = remember(invoices, start, end) {
        invoices.filter {
            it.documentType == InvoiceDocumentType.TAX_INVOICE &&
                it.status != InvoiceStatus.DRAFT &&
                it.status != InvoiceStatus.CANCELLED &&
                it.invoiceDateMillis in start..end
        }
    }
    val gstPurchases = remember(purchases, start, end) {
        purchases.filter { it.purchaseDateMillis in start..end }
    }

    val outputCgst = remember(gstSales) { gstSales.sumOf { it.cgstPaise } }
    val outputSgst = remember(gstSales) { gstSales.sumOf { it.sgstPaise } }
    val outputIgst = remember(gstSales) { gstSales.sumOf { it.igstPaise } }
    val totalOutputTax = remember(gstSales) { gstSales.sumOf { it.totalTaxPaise } }
    val totalInputTax = remember(gstPurchases) { gstPurchases.sumOf { it.totalTaxPaise } }
    val netGstPayable = totalOutputTax - totalInputTax

    Scaffold(topBar = { HisabPulseTopBar(title = "GST & Tax Reports", onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateRangeFilterBar(filter, { f, s, e -> filter = f; customStart = s; customEnd = e }) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        text = stringResource(R.string.gst_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("GST Summary (${filter.label})", style = MaterialTheme.typography.titleMedium)
                        GstLine("Taxable Sales", gstSales.sumOf { it.taxableAmountPaise }, currency)
                        GstLine("Output CGST", outputCgst, currency)
                        GstLine("Output SGST", outputSgst, currency)
                        GstLine("Output IGST", outputIgst, currency)
                        GstLine("Total Output GST", totalOutputTax, currency)
                        GstLine("Input GST Credit (Purchases)", totalInputTax, currency)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net GST Liability", fontWeight = FontWeight.Bold)
                            MoneyText(netGstPayable, currency)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                val pdf = withContext(Dispatchers.IO) {
                                    val rows = gstSales.map {
                                        listOf(
                                            it.invoiceNumber,
                                            DateUtils.formatDate(it.invoiceDateMillis),
                                            it.customerGstin.ifBlank { "URD" },
                                            Money.formatPaise(it.taxableAmountPaise, currency),
                                            Money.formatPaise(it.totalTaxPaise, currency),
                                            Money.formatPaise(it.grandTotalPaise, currency)
                                        )
                                    }
                                    PdfGenerator.generateTabularReportPdf(
                                        context, "gst_summary.pdf", "GST Sales & Tax Summary",
                                        "GSTIN: ${profile?.gstin ?: "Not Set"} • Period: ${filter.label}",
                                        listOf("Inv #", "Date", "GSTIN", "Taxable", "GST", "Total"), rows
                                    )
                                }
                                PdfGenerator.shareFile(context, pdf, "application/pdf", "Share GST Report PDF")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Export PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csv = withContext(Dispatchers.IO) {
                                    val rows = gstSales.map {
                                        listOf(
                                            it.invoiceNumber,
                                            DateUtils.formatIsoDate(it.invoiceDateMillis),
                                            it.customerName,
                                            it.customerGstin,
                                            Money.formatPlain(Money.fromPaise(it.taxableAmountPaise)),
                                            Money.formatPlain(Money.fromPaise(it.cgstPaise)),
                                            Money.formatPlain(Money.fromPaise(it.sgstPaise)),
                                            Money.formatPlain(Money.fromPaise(it.igstPaise)),
                                            Money.formatPlain(Money.fromPaise(it.grandTotalPaise))
                                        )
                                    }
                                    PdfGenerator.exportCsv(
                                        context, "gst_sales.csv",
                                        listOf("Invoice", "Date", "Customer", "GSTIN", "Taxable", "CGST", "SGST", "IGST", "Total"),
                                        rows
                                    )
                                }
                                PdfGenerator.shareFile(context, csv, "text/csv", "Share GST CSV")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.TableChart, null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Export CSV")
                    }
                }
            }
            if (gstSales.isEmpty()) {
                item { EmptyStateCard("No GST Sales in Period", "Create GST Tax Invoices to populate GSTR-style summaries.") }
            } else {
                items(gstSales, key = { it.id }) { inv ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${inv.invoiceNumber} • ${inv.customerName}", fontWeight = FontWeight.SemiBold)
                                Text("Taxable ${Money.formatPaise(inv.taxableAmountPaise, currency)} • GST ${Money.formatPaise(inv.totalTaxPaise, currency)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            MoneyText(inv.grandTotalPaise, currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GstLine(label: String, paise: Long, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.formatPaise(paise, currency), fontWeight = FontWeight.Medium)
    }
}

enum class ReportType(val label: String) {
    SALES("Sales Report"),
    PURCHASES("Purchases"),
    EXPENSES("Expenses"),
    CUSTOMER_DUE("Customer Due"),
    SUPPLIER_DUE("Supplier Due"),
    STOCK("Stock Valuation"),
    LOW_STOCK("Low Stock"),
    RETURNS("Sales & Pur Returns")
}

@Composable
fun BusinessReportsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val invoices by app.container.billingRepository.allInvoices.collectAsStateWithLifecycle(emptyList())
    val purchases by app.container.billingRepository.allPurchases.collectAsStateWithLifecycle(emptyList())
    val expenses by app.container.accountingRepository.expenses.collectAsStateWithLifecycle(emptyList())
    val customers by app.container.partyRepository.customers.collectAsStateWithLifecycle(emptyList())
    val suppliers by app.container.partyRepository.suppliers.collectAsStateWithLifecycle(emptyList())
    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var selectedReport by remember { mutableStateOf(ReportType.SALES) }
    var filter by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    val (start, end) = remember(filter, customStart, customEnd) {
        DateUtils.resolveRange(filter, customStart, customEnd)
    }

    val reportRows: List<Pair<String, String>> = remember(
        selectedReport, invoices, purchases, expenses, customers, suppliers, products, start, end, currency
    ) {
        when (selectedReport) {
            ReportType.SALES -> invoices.filter {
                it.status != InvoiceStatus.DRAFT &&
                    it.status != InvoiceStatus.HELD_POS &&
                    it.status != InvoiceStatus.CANCELLED &&
                    it.documentType != InvoiceDocumentType.ESTIMATE &&
                    it.documentType != InvoiceDocumentType.QUOTATION &&
                    it.documentType != InvoiceDocumentType.PROFORMA &&
                    it.documentType != InvoiceDocumentType.DELIVERY_CHALLAN &&
                    it.documentType != InvoiceDocumentType.SALES_RETURN &&
                    it.documentType != InvoiceDocumentType.CREDIT_NOTE &&
                    it.documentType != InvoiceDocumentType.DEBIT_NOTE &&
                    it.invoiceDateMillis in start..end
            }.map { "${it.invoiceNumber} • ${it.customerName}" to Money.formatPaise(it.grandTotalPaise, currency) }
            ReportType.PURCHASES -> purchases.filter { it.purchaseDateMillis in start..end }
                .map { "${it.purchaseNumber} • ${it.supplierName}" to Money.formatPaise(it.grandTotalPaise, currency) }
            ReportType.EXPENSES -> expenses.filter { it.expenseDateMillis in start..end }
                .map { "${it.categoryName} (${DateUtils.formatDate(it.expenseDateMillis)})" to Money.formatPaise(it.amountPaise, currency) }
            ReportType.CUSTOMER_DUE -> customers.filter { it.currentBalancePaise > 0L }
                .map { it.name to Money.formatPaise(it.currentBalancePaise, currency) }
            ReportType.SUPPLIER_DUE -> suppliers.filter { it.currentBalancePaise > 0L }
                .map { it.name to Money.formatPaise(it.currentBalancePaise, currency) }
            ReportType.STOCK -> products.filter { !it.isServiceItem }
                .map {
                    val valPaise = InventoryRepository.computeStockValuePaise(it.currentStockScaled, it.purchasePricePaise)
                    "${it.name} (${Money.formatQty(Money.fromPaise(it.currentStockScaled))} ${it.unitName})" to Money.formatPaise(valPaise, currency)
                }
            ReportType.LOW_STOCK -> products.filter { !it.isServiceItem && it.currentStockScaled <= it.minStockScaled }
                .map { it.name to "${Money.formatQty(Money.fromPaise(it.currentStockScaled))} ${it.unitName}" }
            ReportType.RETURNS -> {
                val sr = invoices.filter { it.documentType == InvoiceDocumentType.SALES_RETURN && it.invoiceDateMillis in start..end }
                    .map { "SR ${it.invoiceNumber} • ${it.customerName}" to Money.formatPaise(it.grandTotalPaise, currency) }
                val pr = purchases.filter { it.documentType == PurchaseDocumentType.PURCHASE_RETURN && it.purchaseDateMillis in start..end }
                    .map { "PR ${it.purchaseNumber} • ${it.supplierName}" to Money.formatPaise(it.grandTotalPaise, currency) }
                sr + pr
            }
        }
    }

    Scaffold(topBar = { HisabPulseTopBar(title = "Business Reports", onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportType.entries.forEach { rt ->
                        FilterChip(selected = selectedReport == rt, onClick = { selectedReport = rt }, label = { Text(rt.label) })
                    }
                }
            }
            item { DateRangeFilterBar(filter, { f, s, e -> filter = f; customStart = s; customEnd = e }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                val pdf = withContext(Dispatchers.IO) {
                                    PdfGenerator.generateTabularReportPdf(
                                        context,
                                        "report_${selectedReport.name.lowercase()}.pdf",
                                        selectedReport.label,
                                        "Period: ${filter.label}",
                                        listOf("Particulars", "Value"),
                                        reportRows.map { listOf(it.first, it.second) }
                                    )
                                }
                                PdfGenerator.shareFile(context, pdf, "application/pdf", "Share ${selectedReport.label}")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Share PDF") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csv = withContext(Dispatchers.IO) {
                                    PdfGenerator.exportCsv(
                                        context,
                                        "report_${selectedReport.name.lowercase()}.csv",
                                        listOf("Particulars", "Value"),
                                        reportRows.map { listOf(it.first, it.second) }
                                    )
                                }
                                PdfGenerator.shareFile(context, csv, "text/csv", "Share ${selectedReport.label} CSV")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Share CSV") }
                }
            }
            if (reportRows.isEmpty()) {
                item { EmptyStateCard("No Records", "No records found for ${selectedReport.label} in ${filter.label}.") }
            } else {
                items(reportRows) { (left, right) ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(left, modifier = Modifier.weight(1f))
                            Text(right, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
