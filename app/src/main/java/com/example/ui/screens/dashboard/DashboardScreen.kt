package com.example.ui.screens.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppContainer
import com.example.core.DateRangeFilter
import com.example.core.DateUtils
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.JournalEntry
import com.example.ui.HisabViewModelFactory
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.MoneyText
import com.example.ui.components.MonthlyChartPoint
import com.example.ui.components.MonthlySalesExpenseChart
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardUiState(
    val businessName: String = "HisabPulse",
    val currencySymbol: String = "₹",
    val hideAmounts: Boolean = false,
    val selectedFilter: DateRangeFilter = DateRangeFilter.TODAY,
    val salesPaise: Long = 0L,
    val purchasesPaise: Long = 0L,
    val expensesPaise: Long = 0L,
    val profitPaise: Long = 0L,
    val receivablesPaise: Long = 0L,
    val payablesPaise: Long = 0L,
    val cashBalancePaise: Long = 0L,
    val bankBalancePaise: Long = 0L,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val pendingInvoicesCount: Int = 0,
    val customerOutstandingCount: Int = 0,
    val supplierOutstandingCount: Int = 0,
    val recentInvoices: List<Invoice> = emptyList(),
    val recentTransactions: List<JournalEntry> = emptyList(),
    val monthlyChart: List<MonthlyChartPoint> = emptyList(),
    val hasAnyBusinessData: Boolean = false
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val filterState = MutableStateFlow(Triple(DateRangeFilter.TODAY, null as Long?, null as Long?))

    private val coreStreams = combine(
        container.businessRepository.businessProfile,
        container.businessRepository.appSettings,
        container.billingRepository.allInvoices,
        container.billingRepository.allPurchases,
        container.accountingRepository.expenses
    ) { profile, settings, invoices, purchases, expenses ->
        CoreBundle(profile, settings, invoices, purchases, expenses)
    }

    private val partyAndStockStreams = combine(
        container.partyRepository.customers,
        container.partyRepository.suppliers,
        container.inventoryRepository.lowStockProducts,
        container.inventoryRepository.outOfStockProducts,
        container.accountingRepository.cashBalancePaise,
        container.accountingRepository.bankBalancePaise,
        container.accountingRepository.journalEntries
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        PartyStockBundle(
            customers = array[0] as List<com.example.data.local.entities.Customer>,
            suppliers = array[1] as List<com.example.data.local.entities.Supplier>,
            lowStock = array[2] as List<com.example.data.local.entities.Product>,
            outOfStock = array[3] as List<com.example.data.local.entities.Product>,
            cashPaise = array[4] as Long,
            bankPaise = array[5] as Long,
            journals = array[6] as List<JournalEntry>
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        filterState,
        coreStreams,
        partyAndStockStreams
    ) { (filter, customStart, customEnd), core, ps ->
        val (start, end) = DateUtils.resolveRange(filter, customStart, customEnd)
        val validSalesInvoices = core.invoices.filter {
            it.status != InvoiceStatus.DRAFT &&
                it.status != InvoiceStatus.HELD_POS &&
                it.status != InvoiceStatus.CANCELLED &&
                it.documentType != InvoiceDocumentType.ESTIMATE &&
                it.documentType != InvoiceDocumentType.QUOTATION &&
                it.documentType != InvoiceDocumentType.PROFORMA &&
                it.documentType != InvoiceDocumentType.SALES_RETURN &&
                it.documentType != InvoiceDocumentType.CREDIT_NOTE
        }
        val filteredSales = validSalesInvoices
            .filter { it.invoiceDateMillis in start..end }
            .sumOf { it.grandTotalPaise }
        val filteredReturns = core.invoices
            .filter {
                (it.documentType == InvoiceDocumentType.SALES_RETURN || it.documentType == InvoiceDocumentType.CREDIT_NOTE) &&
                    it.status != InvoiceStatus.CANCELLED &&
                    it.invoiceDateMillis in start..end
            }
            .sumOf { it.grandTotalPaise }
        val netSales = (filteredSales - filteredReturns).coerceAtLeast(0L)

        val filteredPurchases = core.purchases
            .filter { it.purchaseDateMillis in start..end }
            .sumOf { it.grandTotalPaise }

        val filteredExpenses = core.expenses
            .filter { it.expenseDateMillis in start..end }
            .sumOf { it.amountPaise }

        val periodProfit = netSales - filteredPurchases - filteredExpenses
        val receivables = ps.customers.filter { it.currentBalancePaise > 0L }.sumOf { it.currentBalancePaise }
        val payables = ps.suppliers.filter { it.currentBalancePaise > 0L }.sumOf { it.currentBalancePaise }
        val pendingCount = validSalesInvoices.count { it.balanceDuePaise > 0L }

        // Build 6-month chart from actual invoices & expenses
        val chart = buildLast6MonthsChart(validSalesInvoices, core.expenses)

        DashboardUiState(
            businessName = core.profile?.businessName ?: "HisabPulse",
            currencySymbol = core.profile?.currencySymbol ?: "₹",
            hideAmounts = core.settings?.hideAmounts ?: false,
            selectedFilter = filter,
            salesPaise = netSales,
            purchasesPaise = filteredPurchases,
            expensesPaise = filteredExpenses,
            profitPaise = periodProfit,
            receivablesPaise = receivables,
            payablesPaise = payables,
            cashBalancePaise = ps.cashPaise,
            bankBalancePaise = ps.bankPaise,
            lowStockCount = ps.lowStock.size,
            outOfStockCount = ps.outOfStock.size,
            pendingInvoicesCount = pendingCount,
            customerOutstandingCount = ps.customers.count { it.currentBalancePaise > 0L },
            supplierOutstandingCount = ps.suppliers.count { it.currentBalancePaise > 0L },
            recentInvoices = core.invoices.take(6),
            recentTransactions = ps.journals.take(6),
            monthlyChart = chart,
            hasAnyBusinessData = core.invoices.isNotEmpty() || core.purchases.isNotEmpty() || core.expenses.isNotEmpty()
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setFilter(filter: DateRangeFilter, customStart: Long?, customEnd: Long?) {
        filterState.value = Triple(filter, customStart, customEnd)
    }

    fun toggleHideAmounts() {
        viewModelScope.launch {
            container.businessRepository.updateAppSettings { it.copy(hideAmounts = !it.hideAmounts) }
        }
    }

    private fun buildLast6MonthsChart(
        invoices: List<Invoice>,
        expenses: List<com.example.data.local.entities.Expense>
    ): List<MonthlyChartPoint> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)
        val labels = Array(6) { "" }
        val salesBuckets = LongArray(6)
        val expenseBuckets = LongArray(6)
        val keyToIndex = HashMap<Int, Int>(8)

        for (i in 0 until 6) {
            val yearMonthKey = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            keyToIndex[yearMonthKey] = i
            labels[i] = DateUtils.formatMonthShort(cal.timeInMillis)
            cal.add(Calendar.MONTH, 1)
        }

        for (inv in invoices) {
            cal.timeInMillis = inv.invoiceDateMillis
            val idx = keyToIndex[cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)]
            if (idx != null) {
                salesBuckets[idx] += inv.grandTotalPaise
            }
        }
        for (exp in expenses) {
            cal.timeInMillis = exp.expenseDateMillis
            val idx = keyToIndex[cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)]
            if (idx != null) {
                expenseBuckets[idx] += exp.amountPaise
            }
        }

        return List(6) { idx ->
            MonthlyChartPoint(labels[idx], salesBuckets[idx], expenseBuckets[idx])
        }
    }

    private data class CoreBundle(
        val profile: com.example.data.local.entities.BusinessProfile?,
        val settings: com.example.data.local.entities.AppSettings?,
        val invoices: List<Invoice>,
        val purchases: List<com.example.data.local.entities.Purchase>,
        val expenses: List<com.example.data.local.entities.Expense>
    )

    private data class PartyStockBundle(
        val customers: List<com.example.data.local.entities.Customer>,
        val suppliers: List<com.example.data.local.entities.Supplier>,
        val lowStock: List<com.example.data.local.entities.Product>,
        val outOfStock: List<com.example.data.local.entities.Product>,
        val cashPaise: Long,
        val bankPaise: Long,
        val journals: List<JournalEntry>
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSalesOverview: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenProfitAndLoss: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenCashbook: () -> Unit,
    onOpenBankAccounts: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCreateInvoice: () -> Unit,
    onOpenPos: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenInvoiceDetail: (Long) -> Unit,
    onOpenJournalEntries: () -> Unit,
    onOpenReports: () -> Unit
) {
    val vm: DashboardViewModel = viewModel(factory = HisabViewModelFactory { DashboardViewModel(it) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.example.HisabPulseApp
    val syncStatus by app.container.cloudSyncRepository.syncStatus.collectAsStateWithLifecycle()
    val semantic = LocalSemanticColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.businessName, style = MaterialTheme.typography.titleLarge)
                        Text("Business Overview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (syncStatus.isStarted) {
                        val (syncLabel, syncIcon, syncTint) = when {
                            syncStatus.hasPendingWrites -> Triple("Offline Queue", Icons.Default.CloudOff, semantic.warning)
                            syncStatus.isSyncing -> Triple("Syncing", Icons.Default.Sync, MaterialTheme.colorScheme.primary)
                            else -> Triple("Synced", Icons.Default.CloudDone, semantic.success)
                        }
                        Surface(
                            color = syncTint.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(syncIcon, contentDescription = syncLabel, tint = syncTint, modifier = Modifier.height(14.dp))
                                Text(syncLabel, style = MaterialTheme.typography.labelSmall, color = syncTint, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    IconButton(onClick = vm::toggleHideAmounts, modifier = Modifier.testTag("toggle_hide_amounts")) {
                        Icon(
                            imageVector = if (state.hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.hideAmounts) "Show amounts" else "Hide amounts"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DateRangeFilterBar(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = vm::setFilter
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip("New Bill", Icons.AutoMirrored.Filled.ReceiptLong, onOpenCreateInvoice)
                    QuickActionChip("Counter POS", Icons.Default.PointOfSale, onOpenPos)
                    QuickActionChip("Receive Pay", Icons.Default.Payments, onOpenPayments)
                    QuickActionChip("Add Expense", Icons.Default.AttachMoney, onOpenExpenses)
                    QuickActionChip("Purchase", Icons.Default.AddShoppingCart, onOpenPurchases)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMetricCard(
                        title = "Sales (${state.selectedFilter.label})",
                        valuePaise = state.salesPaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenSalesOverview,
                        modifier = Modifier.weight(1f),
                        testTag = "dash_sales_card"
                    )
                    KpiMetricCard(
                        title = "Purchases",
                        valuePaise = state.purchasesPaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.ShoppingBag,
                        accentColor = semantic.warning,
                        onClick = onOpenPurchases,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMetricCard(
                        title = "Expenses",
                        valuePaise = state.expensesPaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.Receipt,
                        accentColor = semantic.danger,
                        onClick = onOpenExpenses,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Net Margin",
                        valuePaise = state.profitPaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.AttachMoney,
                        accentColor = if (state.profitPaise >= 0L) semantic.success else semantic.danger,
                        onClick = onOpenProfitAndLoss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMetricCard(
                        title = "Receivables (${state.customerOutstandingCount})",
                        valuePaise = state.receivablesPaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.People,
                        accentColor = semantic.success,
                        subtitle = "${state.pendingInvoicesCount} due",
                        onClick = onOpenCustomers,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Payables (${state.supplierOutstandingCount})",
                        valuePaise = state.payablesPaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.People,
                        accentColor = semantic.danger,
                        onClick = onOpenSuppliers,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMetricCard(
                        title = "Cash Balance",
                        valuePaise = state.cashBalancePaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenCashbook,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Bank Balance",
                        valuePaise = state.bankBalancePaise,
                        currencySymbol = state.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        icon = Icons.Default.AccountBalance,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenBankAccounts,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMetricCard(
                        title = "Low Stock Items",
                        customValueText = "${state.lowStockCount}",
                        icon = Icons.Default.Warning,
                        accentColor = semantic.warning,
                        onClick = onOpenInventory,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Out of Stock",
                        customValueText = "${state.outOfStockCount}",
                        icon = Icons.Default.Inventory2,
                        accentColor = semantic.danger,
                        onClick = onOpenInventory,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                MonthlySalesExpenseChart(
                    points = state.monthlyChart,
                    modifier = Modifier.clickable(onClick = onOpenReports)
                )
            }

            if (!state.hasAnyBusinessData) {
                item {
                    EmptyStateCard(
                        title = "No Business Transactions Yet",
                        description = "Add your products and create your first sales invoice or purchase entry. All dashboard numbers update live from your local ledger.",
                        actionLabel = "Create First Invoice",
                        onAction = onOpenCreateInvoice
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Invoices", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "View Sales",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(onClick = onOpenSalesOverview)
                        )
                    }
                }
                items(state.recentInvoices, key = { "inv_${it.id}" }) { inv ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenInvoiceDetail(inv.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${inv.invoiceNumber} • ${inv.customerName}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${inv.documentType.label} • ${DateUtils.formatDate(inv.invoiceDateMillis)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(inv.grandTotalPaise, state.currencySymbol, state.hideAmounts)
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusPill(
                                    label = inv.status.label,
                                    containerColor = when (inv.status) {
                                        InvoiceStatus.PAID -> semantic.successContainer
                                        InvoiceStatus.PARTIAL -> semantic.warningContainer
                                        InvoiceStatus.CANCELLED -> semantic.dangerContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = when (inv.status) {
                                        InvoiceStatus.PAID -> semantic.success
                                        InvoiceStatus.PARTIAL -> semantic.warning
                                        InvoiceStatus.CANCELLED -> semantic.danger
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                    }
                }

                if (state.recentTransactions.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Ledger Activity", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Journal",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable(onClick = onOpenJournalEntries)
                            )
                        }
                    }
                    items(state.recentTransactions, key = { "je_${it.id}" }) { je ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(je.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text("${je.entryNumber} • ${DateUtils.formatDate(je.entryDateMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                MoneyText(je.totalDebitPaise, state.currencySymbol, state.hideAmounts, fontSizeSp = 14)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) }
    )
}

@Composable
fun SalesOverviewScreen(
    onBack: () -> Unit,
    onOpenInvoiceDetail: (Long) -> Unit,
    onCreateInvoice: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.example.HisabPulseApp
    val invoices by app.container.billingRepository.allInvoices.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false

    var filter by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    val (start, end) = remember(filter, customStart, customEnd) {
        DateUtils.resolveRange(filter, customStart, customEnd)
    }

    val sales = remember(invoices, start, end) {
        invoices.filter {
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
        }
    }
    val returnsPaise = remember(invoices, start, end) {
        invoices.filter {
            (it.documentType == InvoiceDocumentType.SALES_RETURN || it.documentType == InvoiceDocumentType.CREDIT_NOTE) &&
                it.status != InvoiceStatus.DRAFT &&
                it.status != InvoiceStatus.HELD_POS &&
                it.status != InvoiceStatus.CANCELLED &&
                it.invoiceDateMillis in start..end
        }.sumOf { it.grandTotalPaise }
    }
    val grossSales = remember(sales) { sales.sumOf { it.grandTotalPaise } }
    val totalSales = remember(grossSales, returnsPaise) { (grossSales - returnsPaise).coerceAtLeast(0L) }
    val totalCollected = remember(sales) { sales.sumOf { it.paidAmountPaise } }
    val totalDue = remember(sales) { sales.sumOf { it.balanceDuePaise } }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Sales Overview", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DateRangeFilterBar(filter, { f, s, e -> filter = f; customStart = s; customEnd = e })
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Net Sales (${sales.size} invoices)", style = MaterialTheme.typography.titleMedium)
                        MoneyText(totalSales, currency, hide, fontSizeSp = 24)
                        if (returnsPaise > 0L) {
                            Text(
                                "Gross: ${com.example.core.Money.formatPaise(grossSales, currency, hide)} • Returns: -${com.example.core.Money.formatPaise(returnsPaise, currency, hide)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Collected", style = MaterialTheme.typography.labelSmall)
                                MoneyText(totalCollected, currency, hide, fontSizeSp = 14)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Pending Due", style = MaterialTheme.typography.labelSmall)
                                MoneyText(totalDue, currency, hide, fontSizeSp = 14)
                            }
                        }
                    }
                }
            }
            if (sales.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Sales in Selected Period",
                        description = "Create an invoice or switch the date filter to view historical sales.",
                        actionLabel = "Create Invoice",
                        onAction = onCreateInvoice
                    )
                }
            } else {
                items(sales, key = { it.id }) { inv ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenInvoiceDetail(inv.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("${inv.invoiceNumber} • ${inv.customerName}", style = MaterialTheme.typography.titleMedium)
                                Text(DateUtils.formatDate(inv.invoiceDateMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MoneyText(inv.grandTotalPaise, currency, hide)
                        }
                    }
                }
            }
        }
    }
}
