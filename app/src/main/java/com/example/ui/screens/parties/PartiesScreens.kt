package com.example.ui.screens.parties

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.core.DateUtils
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.local.entities.Customer
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Supplier
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    onOpenCustomerDetail: (Long) -> Unit,
    onOpenSuppliers: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val customers by app.container.partyRepository.customers.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false
    val semantic = LocalSemanticColors.current

    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("All") } // All, Due, Clear
    var sortByBalance by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val list = remember(customers, search, filterMode, sortByBalance) {
        customers
            .filter { c ->
                val qOk = search.isBlank() || c.name.contains(search, true) || c.phone.contains(search, true)
                val fOk = when (filterMode) {
                    "Due" -> c.currentBalancePaise > 0L
                    "Clear" -> c.currentBalancePaise <= 0L
                    else -> true
                }
                qOk && fOk
            }
            .let { if (sortByBalance) it.sortedByDescending { c -> c.currentBalancePaise } else it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parties & Khata") },
                actions = {
                    AssistChip(
                        onClick = onOpenSuppliers,
                        label = { Text("Suppliers") },
                        leadingIcon = { Icon(Icons.Default.Store, null) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("fab_add_customer")) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SearchField(query = search, onQueryChange = { search = it }, placeholder = "Search customer name or phone")
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Due", "Clear").forEach { f ->
                        FilterChip(selected = filterMode == f, onClick = { filterMode = f }, label = { Text(f) })
                    }
                    FilterChip(selected = sortByBalance, onClick = { sortByBalance = !sortByBalance }, label = { Text("Highest Due") })
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Customers Yet",
                        description = "Add customers to track Khata balances, due dates, advances, and draft bills.",
                        actionLabel = "Add Customer",
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                items(list, key = { it.id }) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenCustomerDetail(c.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(c.phone.takeIf { it.isNotBlank() }, c.category).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(
                                    c.currentBalancePaise,
                                    currency,
                                    hide,
                                    color = if (c.currentBalancePaise > 0L) semantic.danger else semantic.success
                                )
                                Text(if (c.currentBalancePaise > 0L) "To Collect" else "Settled", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CustomerEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { customer ->
                scope.launch {
                    app.container.partyRepository.saveCustomer(customer)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun CustomerDetailScreen(
    customerId: Long,
    onBack: () -> Unit,
    onOpenLedger: (Long) -> Unit,
    onCreateInvoiceForCustomer: (Long) -> Unit,
    onEditDraftInvoice: (Long, InvoiceDocumentType) -> Unit,
    onOpenInvoiceDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val customer by app.container.partyRepository.observeCustomer(customerId).collectAsStateWithLifecycle(null)
    val invoices by app.container.billingRepository.observeCustomerInvoices(customerId).collectAsStateWithLifecycle(emptyList())
    val payments by app.container.accountingRepository.observeCustomerPayments(customerId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false
    val semantic = LocalSemanticColors.current

    var showEditDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }

    val drafts = remember(invoices) { invoices.filter { it.status == InvoiceStatus.DRAFT } }
    val finalized = remember(invoices) { invoices.filter { it.status != InvoiceStatus.DRAFT && it.status != InvoiceStatus.HELD_POS } }
    val totalPurchases = remember(finalized) {
        finalized.filter { it.status != InvoiceStatus.CANCELLED && it.documentType != InvoiceDocumentType.SALES_RETURN }
            .sumOf { it.grandTotalPaise }
    }
    val totalPaid = remember(payments) { payments.sumOf { it.amountPaise } }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = customer?.name ?: "Customer Khata",
                subtitle = customer?.category,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit customer") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val c = customer ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Outstanding Due", style = MaterialTheme.typography.labelSmall)
                                MoneyText(
                                    c.currentBalancePaise,
                                    currency,
                                    hide,
                                    color = if (c.currentBalancePaise > 0L) semantic.danger else semantic.success,
                                    fontSizeSp = 22
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Advance Balance", style = MaterialTheme.typography.labelSmall)
                                MoneyText(c.advanceBalancePaise, currency, hide, color = semantic.success, fontSizeSp = 18)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Purchases: ${Money.formatPaise(totalPurchases, currency, hide)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Total Paid: ${Money.formatPaise(totalPaid, currency, hide)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (c.phone.isNotBlank() || c.gstin.isNotBlank()) {
                            Text("${c.phone}  ${c.gstin}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { showPayDialog = true }) {
                        Icon(Icons.Default.Payments, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Receive Payment")
                    }
                    OutlinedButton(onClick = { onCreateInvoiceForCustomer(customerId) }) {
                        Icon(Icons.Default.ReceiptLong, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Bill")
                    }
                    OutlinedButton(onClick = { onOpenLedger(customerId) }) {
                        Icon(Icons.Default.Book, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Khata Ledger")
                    }
                    if (c.phone.isNotBlank()) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")))
                        }) {
                            Icon(Icons.Default.Call, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call")
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:${c.phone}")))
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Message, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Message")
                        }
                    }
                }
            }

            // CRITICAL WORKFLOW: Open and edit existing draft bills directly from customer profile
            if (drafts.isNotEmpty()) {
                item {
                    Text("Draft Bills (Tap to Edit)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                items(drafts, key = { "draft_${it.id}" }) { draft ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onEditDraftInvoice(draft.id, draft.documentType) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${draft.invoiceNumber} • DRAFT", style = MaterialTheme.typography.titleMedium)
                                Text("Tap to add/remove items, edit qty/rate/tax, or finalize", style = MaterialTheme.typography.bodyMedium)
                            }
                            MoneyText(draft.grandTotalPaise, currency, hide)
                        }
                    }
                }
            }

            item { Text("Invoice & Return History (${finalized.size})", style = MaterialTheme.typography.titleMedium) }
            if (finalized.isEmpty()) {
                item { Text("No finalized invoices for this customer yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(finalized, key = { "inv_${it.id}" }) { inv ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenInvoiceDetail(inv.id) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${inv.invoiceNumber} • ${inv.documentType.label}", style = MaterialTheme.typography.titleMedium)
                                Text(DateUtils.formatDate(inv.invoiceDateMillis), style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(inv.grandTotalPaise, currency, hide)
                                StatusPill(inv.status.label, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            item { Text("Payment History (${payments.size})", style = MaterialTheme.typography.titleMedium) }
            items(payments, key = { "pay_${it.id}" }) { p ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${p.receiptNumber} • ${p.direction.label}", fontWeight = FontWeight.Medium)
                            Text("${p.paymentMode.label} • ${DateUtils.formatDate(p.paymentDateMillis)}", style = MaterialTheme.typography.labelSmall)
                        }
                        MoneyText(p.amountPaise, currency, hide, color = semantic.success)
                    }
                }
            }
        }
    }

    if (showEditDialog && customer != null) {
        CustomerEditDialog(
            initial = customer,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                scope.launch {
                    app.container.partyRepository.saveCustomer(updated)
                    showEditDialog = false
                }
            }
        )
    }

    if (showPayDialog && customer != null) {
        var amt by remember { mutableStateOf(if (customer!!.currentBalancePaise > 0L) Money.formatPlain(Money.fromPaise(customer!!.currentBalancePaise)) else "") }
        var mode by remember { mutableStateOf(PaymentMode.CASH) }
        var isAdvance by remember { mutableStateOf(false) }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Receive Payment from ${customer!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Record as Advance")
                        Switch(checked = isAdvance, onCheckedChange = { isAdvance = it })
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.accountingRepository.recordCustomerPayment(
                            customerId = customerId,
                            amountPaise = Money.safeToPaise(Money.of(amt)),
                            paymentMode = mode,
                            isAdvance = isAdvance,
                            notes = notes
                        ).onSuccess {
                            showPayDialog = false
                            snackbar.showSnackbar("Payment recorded & Khata updated")
                        }.onFailure { snackbar.showSnackbar(it.message ?: "Error") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showPayDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CustomerLedgerScreen(customerId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val customer by app.container.partyRepository.observeCustomer(customerId).collectAsStateWithLifecycle(null)
    val entries by app.container.partyRepository.observeCustomerLedger(customerId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "${customer?.name ?: "Customer"} Ledger",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val pdf = withContext(Dispatchers.IO) {
                                val rows = entries.map {
                                    listOf(
                                        DateUtils.formatDate(it.entryDateMillis),
                                        it.narration,
                                        Money.formatPaise(it.debitPaise, currency),
                                        Money.formatPaise(it.creditPaise, currency)
                                    )
                                }
                                PdfGenerator.generateTabularReportPdf(
                                    context = context,
                                    fileName = "customer_ledger_$customerId.pdf",
                                    title = "Customer Khata Ledger - ${customer?.name ?: ""}",
                                    subtitle = "Closing Balance: ${Money.formatPaise(customer?.currentBalancePaise ?: 0L, currency)}",
                                    headers = listOf("Date", "Particulars", "Debit", "Credit"),
                                    rows = rows
                                )
                            }
                            PdfGenerator.shareFile(context, pdf, "application/pdf", "Share Customer Ledger")
                        }
                    }) { Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Ledger PDF") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entries.isEmpty()) {
                item { EmptyStateCard("No Ledger Entries", "Finalized invoices and customer payments appear here automatically.") }
            } else {
                items(entries, key = { it.id }) { e ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.narration, fontWeight = FontWeight.Medium)
                                Text(DateUtils.formatDate(e.entryDateMillis), style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (e.debitPaise > 0L) Text("Dr ${Money.formatPaise(e.debitPaise, currency)}", color = MaterialTheme.colorScheme.error)
                                if (e.creditPaise > 0L) Text("Cr ${Money.formatPaise(e.creditPaise, currency)}", color = LocalSemanticColors.current.success)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuppliersScreen(
    onBack: () -> Unit,
    onOpenSupplierDetail: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val suppliers by app.container.partyRepository.suppliers.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false

    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    val filtered = remember(suppliers, search) {
        suppliers.filter { search.isBlank() || it.name.contains(search, true) || it.phone.contains(search, true) }
    }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Suppliers", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add Supplier") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SearchField(search, { search = it }, "Search suppliers") }
            if (filtered.isEmpty()) {
                item { EmptyStateCard("No Suppliers Yet", "Add suppliers to record purchases, payables, and supplier statements.", "Add Supplier", { showAdd = true }) }
            } else {
                items(filtered, key = { it.id }) { s ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenSupplierDetail(s.id) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                if (s.phone.isNotBlank()) Text(s.phone, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(s.currentBalancePaise, currency, hide)
                                Text("Payable", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        SupplierEditDialog(null, { showAdd = false }) { supplier ->
            scope.launch {
                app.container.partyRepository.saveSupplier(supplier)
                showAdd = false
            }
        }
    }
}

@Composable
fun SupplierDetailScreen(
    supplierId: Long,
    onBack: () -> Unit,
    onOpenLedger: (Long) -> Unit,
    onOpenPurchaseDetail: (Long) -> Unit,
    onCreatePurchase: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val supplier by app.container.partyRepository.observeSupplier(supplierId).collectAsStateWithLifecycle(null)
    val purchases by app.container.billingRepository.observeSupplierPurchases(supplierId).collectAsStateWithLifecycle(emptyList())
    val payments by app.container.accountingRepository.observeSupplierPayments(supplierId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var showPay by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = supplier?.name ?: "Supplier",
                onBack = onBack,
                actions = { IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit supplier") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val s = supplier ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Outstanding Payable", style = MaterialTheme.typography.labelSmall)
                        MoneyText(s.currentBalancePaise, currency, fontSizeSp = 22)
                        Text("Advance Paid: ${Money.formatPaise(s.advanceBalancePaise, currency)}")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showPay = true }) { Text("Make Payment") }
                    OutlinedButton(onClick = onCreatePurchase) { Text("New Purchase") }
                    OutlinedButton(onClick = { onOpenLedger(supplierId) }) { Text("Supplier Ledger") }
                }
            }
            item { Text("Purchases (${purchases.size})", style = MaterialTheme.typography.titleMedium) }
            items(purchases, key = { "pur_${it.id}" }) { p ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenPurchaseDetail(p.id) }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${p.purchaseNumber} • ${p.documentType.label}", style = MaterialTheme.typography.titleMedium)
                            Text(DateUtils.formatDate(p.purchaseDateMillis), style = MaterialTheme.typography.bodyMedium)
                        }
                        MoneyText(p.grandTotalPaise, currency)
                    }
                }
            }
            item { Text("Payments (${payments.size})", style = MaterialTheme.typography.titleMedium) }
            items(payments, key = { "sp_${it.id}" }) { pay ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${pay.receiptNumber} • ${pay.paymentMode.label}")
                        MoneyText(pay.amountPaise, currency)
                    }
                }
            }
        }
    }

    if (showEdit && supplier != null) {
        SupplierEditDialog(supplier, { showEdit = false }) { updated ->
            scope.launch { app.container.partyRepository.saveSupplier(updated); showEdit = false }
        }
    }

    if (showPay && supplier != null) {
        var amt by remember { mutableStateOf(if (supplier!!.currentBalancePaise > 0L) Money.formatPlain(Money.fromPaise(supplier!!.currentBalancePaise)) else "") }
        var mode by remember { mutableStateOf(PaymentMode.CASH) }
        var isAdvance by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPay = false },
            title = { Text("Pay ${supplier!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Advance Payment")
                        Switch(checked = isAdvance, onCheckedChange = { isAdvance = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.accountingRepository.recordSupplierPayment(
                            supplierId = supplierId,
                            amountPaise = Money.safeToPaise(Money.of(amt)),
                            paymentMode = mode,
                            isAdvance = isAdvance
                        ).onSuccess {
                            showPay = false
                            snackbar.showSnackbar("Supplier payment recorded")
                        }.onFailure { snackbar.showSnackbar(it.message ?: "Error") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showPay = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SupplierLedgerScreen(supplierId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val supplier by app.container.partyRepository.observeSupplier(supplierId).collectAsStateWithLifecycle(null)
    val entries by app.container.partyRepository.observeSupplierLedger(supplierId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "${supplier?.name ?: "Supplier"} Ledger",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val pdf = withContext(Dispatchers.IO) {
                                PdfGenerator.generateTabularReportPdf(
                                    context = context,
                                    fileName = "supplier_ledger_$supplierId.pdf",
                                    title = "Supplier Statement - ${supplier?.name ?: ""}",
                                    subtitle = "Payable Balance: ${Money.formatPaise(supplier?.currentBalancePaise ?: 0L, currency)}",
                                    headers = listOf("Date", "Particulars", "Debit", "Credit"),
                                    rows = entries.map { listOf(DateUtils.formatDate(it.entryDateMillis), it.narration, Money.formatPaise(it.debitPaise, currency), Money.formatPaise(it.creditPaise, currency)) }
                                )
                            }
                            PdfGenerator.shareFile(context, pdf, "application/pdf", "Share Supplier Ledger")
                        }
                    }) { Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries.isEmpty()) {
                item { EmptyStateCard("No Supplier Entries", "Purchases and supplier payments appear here automatically.") }
            } else {
                items(entries, key = { it.id }) { e ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.narration, fontWeight = FontWeight.Medium)
                                Text(DateUtils.formatDate(e.entryDateMillis), style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (e.debitPaise > 0L) Text("Dr ${Money.formatPaise(e.debitPaise, currency)}")
                                if (e.creditPaise > 0L) Text("Cr ${Money.formatPaise(e.creditPaise, currency)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerEditDialog(initial: Customer?, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var gstin by remember { mutableStateOf(initial?.gstin ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Regular") }
    var opening by remember { mutableStateOf(if (initial != null) Money.formatPlain(Money.fromPaise(initial.openingBalancePaise)) else "0") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Customer" else "Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name *") }, modifier = Modifier.testTag("customer_name_input"))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
                OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN") })
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Balance") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        (initial ?: Customer(name = name)).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            gstin = gstin.trim(),
                            category = category.trim().ifBlank { "Regular" },
                            openingBalancePaise = Money.safeToPaise(Money.of(opening)),
                            notes = notes.trim()
                        )
                    )
                }
            }, modifier = Modifier.testTag("save_customer_btn")) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SupplierEditDialog(initial: Supplier?, onDismiss: () -> Unit, onSave: (Supplier) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var gstin by remember { mutableStateOf(initial?.gstin ?: "") }
    var opening by remember { mutableStateOf(if (initial != null) Money.formatPlain(Money.fromPaise(initial.openingBalancePaise)) else "0") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Supplier" else "Edit Supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name *") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
                OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN") })
                OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Payable") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        (initial ?: Supplier(name = name)).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            gstin = gstin.trim(),
                            openingBalancePaise = Money.safeToPaise(Money.of(opening)),
                            notes = notes.trim()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
