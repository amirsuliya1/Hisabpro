package com.example.ui.screens.finance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateRangeFilter
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.Expense
import com.example.data.local.entities.PaymentDirection
import com.example.data.local.entities.PaymentMode
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch

@Composable
fun PaymentsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val payments by app.container.accountingRepository.payments.collectAsStateWithLifecycle(emptyList())
    val customers by app.container.partyRepository.customers.collectAsStateWithLifecycle(emptyList())
    val suppliers by app.container.partyRepository.suppliers.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val semantic = LocalSemanticColors.current

    var showRecordDialog by remember { mutableStateOf(false) }

    val todayRange = DateUtils.resolveRange(DateRangeFilter.TODAY)
    val todayCollection = payments.filter {
        it.paymentDateMillis in todayRange.first..todayRange.second &&
            (it.direction == PaymentDirection.RECEIVED || it.direction == PaymentDirection.CUSTOMER_ADVANCE)
    }.sumOf { it.amountPaise }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Payments & Receipts", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showRecordDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Record Payment") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Today's Collections", style = MaterialTheme.typography.titleMedium)
                        MoneyText(todayCollection, currency, fontSizeSp = 22)
                        Text("Offline mode: UPI & Bank transfers are recorded manually.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (payments.isEmpty()) {
                item { EmptyStateCard("No Payments Recorded", "Record customer receipts, supplier payouts, advances, or refunds.", "Record Payment", { showRecordDialog = true }) }
            } else {
                items(payments, key = { it.id }) { p ->
                    val isIncoming = p.direction == PaymentDirection.RECEIVED || p.direction == PaymentDirection.CUSTOMER_ADVANCE || p.direction == PaymentDirection.REFUND_RECEIVED
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.receiptNumber} • ${p.partyName}", style = MaterialTheme.typography.titleMedium)
                                Text("${p.direction.label} • ${p.paymentMode.label} • ${DateUtils.formatDate(p.paymentDateMillis)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            MoneyText(p.amountPaise, currency, color = if (isIncoming) semantic.success else semantic.danger)
                        }
                    }
                }
            }
        }
    }

    if (showRecordDialog) {
        var isCustomer by remember { mutableStateOf(true) }
        var selectedPartyId by remember { mutableStateOf(customers.firstOrNull()?.id ?: suppliers.firstOrNull()?.id) }
        var amt by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf(PaymentMode.CASH) }
        var refNo by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showRecordDialog = false },
            title = { Text("Record Offline Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isCustomer, onClick = { isCustomer = true; selectedPartyId = customers.firstOrNull()?.id }, label = { Text("Receive (Customer)") })
                        FilterChip(selected = !isCustomer, onClick = { isCustomer = false; selectedPartyId = suppliers.firstOrNull()?.id }, label = { Text("Pay (Supplier)") })
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isCustomer) {
                            customers.forEach { c -> FilterChip(selected = selectedPartyId == c.id, onClick = { selectedPartyId = c.id }, label = { Text(c.name) }) }
                        } else {
                            suppliers.forEach { s -> FilterChip(selected = selectedPartyId == s.id, onClick = { selectedPartyId = s.id }, label = { Text(s.name) }) }
                        }
                    }
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                    }
                    OutlinedTextField(value = refNo, onValueChange = { refNo = it }, label = { Text("UPI / Cheque / Ref # (Optional)") })
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val partyId = selectedPartyId ?: return@Button
                    scope.launch {
                        val paise = Money.safeToPaise(Money.of(amt))
                        val res = if (isCustomer) {
                            app.container.accountingRepository.recordCustomerPayment(partyId, paise, mode, referenceNumber = refNo, notes = notes)
                        } else {
                            app.container.accountingRepository.recordSupplierPayment(partyId, paise, mode, referenceNumber = refNo, notes = notes)
                        }
                        res.onSuccess { showRecordDialog = false; snackbar.showSnackbar("Payment saved") }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showRecordDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CashbookScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val accounts by app.container.accountingRepository.accounts.collectAsStateWithLifecycle(emptyList())
    val entries by app.container.accountingRepository.ledgerEntries.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val semantic = LocalSemanticColors.current

    var filter by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var showAdjustDialog by remember { mutableStateOf(false) }

    val cashAcc = remember(accounts) {
        accounts.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.CASH }
    }
    val (start, end) = remember(filter, customStart, customEnd) {
        DateUtils.resolveRange(filter, customStart, customEnd)
    }
    val cashEntries = remember(entries, cashAcc?.id, start, end) {
        entries.filter { it.accountId == cashAcc?.id && it.entryDateMillis in start..end }
    }
    val cashIn = remember(cashEntries) { cashEntries.sumOf { it.debitPaise } }
    val cashOut = remember(cashEntries) { cashEntries.sumOf { it.creditPaise } }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Cashbook", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdjustDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Cash Adjustment") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateRangeFilterBar(filter, { f, s, e -> filter = f; customStart = s; customEnd = e }) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Closing Cash Balance", style = MaterialTheme.typography.labelSmall)
                        MoneyText(cashAcc?.currentBalancePaise ?: 0L, currency, fontSizeSp = 24)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Cash In", style = MaterialTheme.typography.labelSmall)
                                MoneyText(cashIn, currency, color = semantic.success)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Cash Out", style = MaterialTheme.typography.labelSmall)
                                MoneyText(cashOut, currency, color = semantic.danger)
                            }
                        }
                    }
                }
            }
            if (cashEntries.isEmpty()) {
                item { EmptyStateCard("No Cash Transactions", "Cash sales, expenses, payments, and cash adjustments appear here.") }
            } else {
                items(cashEntries, key = { it.id }) { e ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.narration, fontWeight = FontWeight.Medium)
                                Text(DateUtils.formatDateTime(e.entryDateMillis), style = MaterialTheme.typography.labelSmall)
                            }
                            if (e.debitPaise > 0L) MoneyText(e.debitPaise, currency, color = semantic.success)
                            else MoneyText(e.creditPaise, currency, color = semantic.danger)
                        }
                    }
                }
            }
        }
    }

    if (showAdjustDialog) {
        var isCashIn by remember { mutableStateOf(true) }
        var amt by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("Cash drawer adjustment") }
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Cash Adjustment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isCashIn, onClick = { isCashIn = true }, label = { Text("Cash In (+)") })
                        FilterChip(selected = !isCashIn, onClick = { isCashIn = false }, label = { Text("Cash Out (-)") })
                    }
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.accountingRepository.recordCashAdjustment(Money.safeToPaise(Money.of(amt)), isCashIn, notes)
                            .onSuccess { showAdjustDialog = false; snackbar.showSnackbar("Cashbook adjusted") }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Error") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showAdjustDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val expenses by app.container.accountingRepository.expenses.collectAsStateWithLifecycle(emptyList())
    val categories by app.container.accountingRepository.expenseCategories.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf<Expense?>(null) }

    val totalExpenses = expenses.sumOf { it.amountPaise }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Expenses", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingExpense = null; showForm = true }) { Icon(Icons.Default.Add, contentDescription = "Add Expense") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Recorded Expenses", style = MaterialTheme.typography.titleMedium)
                        MoneyText(totalExpenses, currency, fontSizeSp = 22)
                    }
                }
            }
            if (expenses.isEmpty()) {
                item { EmptyStateCard("No Expenses Yet", "Track Rent, Electricity, Salary, Transport, and other operating expenses.", "Add Expense", { showForm = true }) }
            } else {
                items(expenses, key = { it.id }) { exp ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.categoryName, style = MaterialTheme.typography.titleMedium)
                                Text("${exp.paymentMode.label} • ${DateUtils.formatDate(exp.expenseDateMillis)}", style = MaterialTheme.typography.labelSmall)
                                if (exp.description.isNotBlank()) Text(exp.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoneyText(exp.amountPaise, currency)
                                IconButton(onClick = { editingExpense = exp; showForm = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                                IconButton(onClick = { deleteConfirm = exp }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        var catName by remember { mutableStateOf(editingExpense?.categoryName ?: categories.firstOrNull()?.name ?: "Miscellaneous") }
        var amt by remember { mutableStateOf(if (editingExpense != null) Money.formatPlain(Money.fromPaise(editingExpense!!.amountPaise)) else "") }
        var mode by remember { mutableStateOf(editingExpense?.paymentMode ?: PaymentMode.CASH) }
        var desc by remember { mutableStateOf(editingExpense?.description ?: "") }
        var receiptUri by remember { mutableStateOf(editingExpense?.receiptUri ?: "") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) receiptUri = uri.toString()
        }

        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (editingExpense == null) "Add Expense" else "Edit Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c -> FilterChip(selected = catName == c.name, onClick = { catName = c.name }, label = { Text(c.name) }) }
                    }
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                    }
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AttachFile, null)
                        Text(if (receiptUri.isBlank()) "Attach Receipt Photo" else "Receipt Attached")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.accountingRepository.saveExpense(
                            (editingExpense ?: Expense(categoryName = catName, amountPaise = 0L)).copy(
                                categoryName = catName,
                                amountPaise = Money.safeToPaise(Money.of(amt)),
                                paymentMode = mode,
                                description = desc.trim(),
                                receiptUri = receiptUri
                            )
                        ).onSuccess { showForm = false; snackbar.showSnackbar("Expense saved & posted to Cashbook") }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Error") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showForm = false }) { Text("Cancel") } }
        )
    }

    if (deleteConfirm != null) {
        ConfirmActionDialog(
            title = "Delete Expense?",
            message = "A reversing double-entry journal voucher will be posted to keep your cashbook accurate.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    app.container.accountingRepository.deleteExpenseSafely(deleteConfirm!!)
                    deleteConfirm = null
                }
            },
            onDismiss = { deleteConfirm = null }
        )
    }
}

@Composable
fun AccountingScreen(
    onBack: () -> Unit,
    onOpenJournalEntries: () -> Unit,
    onOpenTrialBalance: () -> Unit,
    onOpenProfitAndLoss: () -> Unit,
    onOpenBalanceSheet: () -> Unit,
    onOpenBankAccounts: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val accounts by app.container.accountingRepository.accounts.collectAsStateWithLifecycle(emptyList())
    val receivables by app.container.partyRepository.totalReceivables.collectAsStateWithLifecycle(0L)
    val payables by app.container.partyRepository.totalPayables.collectAsStateWithLifecycle(0L)
    val cash by app.container.accountingRepository.cashBalancePaise.collectAsStateWithLifecycle(0L)
    val bank by app.container.accountingRepository.bankBalancePaise.collectAsStateWithLifecycle(0L)
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val pnl = app.container.accountingRepository.buildProfitAndLoss(accounts)

    Scaffold(topBar = { HisabPulseTopBar(title = "Double-Entry Accounting", onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Financial Position Snapshot", style = MaterialTheme.typography.titleMedium)
                        FinancialLine("Revenue", pnl.salesRevenuePaise, currency)
                        FinancialLine("Net Profit", pnl.netProfitPaise, currency)
                        FinancialLine("Cash Balance", cash, currency)
                        FinancialLine("Bank Balance", bank, currency)
                        FinancialLine("Receivables", receivables, currency)
                        FinancialLine("Payables", payables, currency)
                    }
                }
            }
            item { AccountingMenuCard("Journal Entries", "Auditable double-entry debit/credit vouchers", Icons.Default.MenuBook, onOpenJournalEntries) }
            item { AccountingMenuCard("Trial Balance", "Verify total debits equal total credits", Icons.Default.Balance, onOpenTrialBalance) }
            item { AccountingMenuCard("Profit & Loss Statement", "Revenue, COGS, Gross & Net Profit", Icons.Default.Assessment, onOpenProfitAndLoss) }
            item { AccountingMenuCard("Balance Sheet", "Assets, Liabilities & Owner Equity", Icons.Default.AccountBalance, onOpenBalanceSheet) }
            item { AccountingMenuCard("Bank Accounts", "Manage bank accounts, IFSC & UPI IDs", Icons.Default.AccountBalance, onOpenBankAccounts) }
        }
    }
}

@Composable
private fun FinancialLine(label: String, paise: Long, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(paise, currency, fontSizeSp = 15)
    }
}

@Composable
private fun AccountingMenuCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun JournalEntriesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val entries by app.container.accountingRepository.journalEntries.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    Scaffold(topBar = { HisabPulseTopBar(title = "Journal Entries", onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries.isEmpty()) {
                item { EmptyStateCard("No Journal Entries", "Every sale, purchase, payment, and expense posts a balanced journal entry automatically.") }
            } else {
                items(entries, key = { it.id }) { je ->
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(je.entryNumber, fontWeight = FontWeight.Bold)
                                Text(DateUtils.formatDate(je.entryDateMillis), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(je.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Dr ${Money.formatPaise(je.totalDebitPaise, currency)}  =  Cr ${Money.formatPaise(je.totalCreditPaise, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrialBalanceScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val accounts by app.container.accountingRepository.accounts.collectAsStateWithLifecycle(emptyList())
    val ledgerEntries by app.container.accountingRepository.ledgerEntries.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val semantic = LocalSemanticColors.current

    val rows = app.container.accountingRepository.buildTrialBalance(accounts, ledgerEntries)
    val totalDebits = rows.sumOf { it.totalDebitPaise }
    val totalCredits = rows.sumOf { it.totalCreditPaise }
    val balanced = totalDebits == totalCredits

    Scaffold(topBar = { HisabPulseTopBar(title = "Trial Balance", onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            label = if (balanced) "Balanced (Debits == Credits)" else "Imbalance Detected",
                            containerColor = if (balanced) semantic.successContainer else semantic.dangerContainer,
                            contentColor = if (balanced) semantic.success else semantic.danger
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Debits: ${Money.formatPaise(totalDebits, currency)}", fontWeight = FontWeight.Bold)
                            Text("Total Credits: ${Money.formatPaise(totalCredits, currency)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            items(rows, key = { it.account.id }) { row ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${row.account.code} • ${row.account.name}", fontWeight = FontWeight.SemiBold)
                            Text(row.account.accountType.label, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Dr ${Money.formatPaise(row.totalDebitPaise, currency)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Cr ${Money.formatPaise(row.totalCreditPaise, currency)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfitAndLossScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val accounts by app.container.accountingRepository.accounts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val pnl = app.container.accountingRepository.buildProfitAndLoss(accounts)

    Scaffold(topBar = { HisabPulseTopBar(title = "Profit & Loss", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinancialLine("Sales Revenue", pnl.salesRevenuePaise, currency)
                    FinancialLine("Other Income", pnl.otherIncomePaise, currency)
                    FinancialLine("Cost of Goods Sold (COGS)", pnl.cogsPaise, currency)
                    HorizontalDivider()
                    FinancialLine("Gross Profit", pnl.grossProfitPaise, currency)
                    FinancialLine("Operating Expenses", pnl.operatingExpensesPaise, currency)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Profit", style = MaterialTheme.typography.titleLarge)
                        MoneyText(pnl.netProfitPaise, currency, fontSizeSp = 20)
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceSheetScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val accounts by app.container.accountingRepository.accounts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val bs = app.container.accountingRepository.buildBalanceSheet(accounts)

    Scaffold(topBar = { HisabPulseTopBar(title = "Balance Sheet", onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total Assets: ${Money.formatPaise(bs.totalAssetsPaise, currency)}", fontWeight = FontWeight.Bold)
                        Text("Total Liabilities & Equity: ${Money.formatPaise(bs.totalLiabilitiesAndEquityPaise, currency)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Text("Assets", style = MaterialTheme.typography.titleMedium) }
            items(bs.assetAccounts, key = { "a_${it.id}" }) { acc ->
                Card { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(acc.name); MoneyText(acc.currentBalancePaise, currency) } }
            }
            item { Text("Liabilities", style = MaterialTheme.typography.titleMedium) }
            items(bs.liabilityAccounts, key = { "l_${it.id}" }) { acc ->
                Card { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(acc.name); MoneyText(acc.currentBalancePaise, currency) } }
            }
            item { Text("Equity & Retained Earnings", style = MaterialTheme.typography.titleMedium) }
            items(bs.equityAccounts, key = { "e_${it.id}" }) { acc ->
                Card { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(acc.name); MoneyText(acc.currentBalancePaise, currency) } }
            }
            item {
                Card { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Current Period Net Profit"); MoneyText(bs.netProfitPaise, currency) } }
            }
        }
    }
}

@Composable
fun BankAccountsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val accounts by app.container.accountingRepository.accounts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    val bankAccounts = accounts.filter { it.isBankAccount || it.code == HisabPulseDatabase.Companion.AccountCodes.BANK }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Bank Accounts", onBack = onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add Bank") } }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bankAccounts, key = { it.id }) { bank ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(bank.name, style = MaterialTheme.typography.titleMedium)
                            if (bank.bankAccountNumber.isNotBlank()) Text("A/C: ${bank.bankAccountNumber} • ${bank.bankIfsc}", style = MaterialTheme.typography.labelSmall)
                            if (bank.upiId.isNotBlank()) Text("UPI: ${bank.upiId}", style = MaterialTheme.typography.labelSmall)
                        }
                        MoneyText(bank.currentBalancePaise, currency)
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var acNo by remember { mutableStateOf("") }
        var ifsc by remember { mutableStateOf("") }
        var upi by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Bank Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bank / Account Name *") })
                    OutlinedTextField(value = acNo, onValueChange = { acNo = it }, label = { Text("Account Number") })
                    OutlinedTextField(value = ifsc, onValueChange = { ifsc = it }, label = { Text("IFSC Code") })
                    OutlinedTextField(value = upi, onValueChange = { upi = it }, label = { Text("UPI ID") })
                    OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Balance") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            app.container.accountingRepository.addBankAccount(name, acNo, ifsc, upi, Money.safeToPaise(Money.of(opening)))
                            showAdd = false
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
