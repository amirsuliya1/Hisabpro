package com.example.ui.screens.billing

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.SearchModule
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.RecentSearchHistoryBar
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    onCreateInvoice: (InvoiceDocumentType) -> Unit,
    onEditDraft: (Long, InvoiceDocumentType) -> Unit,
    onOpenInvoiceDetail: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenEstimates: () -> Unit,
    onOpenQuotations: () -> Unit,
    onOpenSalesReturns: () -> Unit,
    onOpenPos: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val invoices by app.container.billingRepository.allInvoices.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false

    val recentSearches by app.container.searchHistoryRepository
        .observeRecentSearches(SearchModule.INVOICE)
        .collectAsStateWithLifecycle(emptyList())

    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<InvoiceStatus?>(null) }

    LaunchedEffect(search) {
        val trimmed = search.trim()
        if (trimmed.length >= 2) {
            delay(900L)
            app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
        }
    }

    val filtered = remember(invoices, search, statusFilter) {
        invoices.filter { inv ->
            val matchesSearch = search.isBlank() ||
                inv.invoiceNumber.contains(search, ignoreCase = true) ||
                inv.customerName.contains(search, ignoreCase = true)
            val matchesStatus = statusFilter == null || inv.status == statusFilter
            matchesSearch && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing & Invoices") },
                actions = {
                    AssistChip(
                        onClick = onOpenPos,
                        label = { Text("POS") },
                        leadingIcon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onCreateInvoice(InvoiceDocumentType.TAX_INVOICE) },
                modifier = Modifier.testTag("billing_fab_new_invoice")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Invoice")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { onCreateInvoice(InvoiceDocumentType.TAX_INVOICE) }, label = { Text("GST Invoice") }, leadingIcon = { Icon(Icons.Default.ReceiptLong, null) })
                    AssistChip(onClick = { onCreateInvoice(InvoiceDocumentType.NON_GST_INVOICE) }, label = { Text("Non-GST Bill") }, leadingIcon = { Icon(Icons.Default.Description, null) })
                    AssistChip(onClick = onOpenEstimates, label = { Text("Estimates") }, leadingIcon = { Icon(Icons.Default.RequestQuote, null) })
                    AssistChip(onClick = onOpenQuotations, label = { Text("Quotations") }, leadingIcon = { Icon(Icons.Default.RequestQuote, null) })
                    AssistChip(onClick = onOpenSalesReturns, label = { Text("Returns") }, leadingIcon = { Icon(Icons.Default.AssignmentReturn, null) })
                    AssistChip(onClick = onOpenHistory, label = { Text("All History") }, leadingIcon = { Icon(Icons.Default.History, null) })
                }
            }

            item {
                SearchField(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "Search invoice # or customer",
                    onSearchSubmit = {
                        val trimmed = search.trim()
                        if (trimmed.length >= 2) {
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                            }
                        }
                    }
                )
            }
            if (recentSearches.isNotEmpty()) {
                item {
                    RecentSearchHistoryBar(
                        history = recentSearches,
                        onSelectQuery = { selected ->
                            search = selected
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, selected)
                            }
                        },
                        onDeleteEntry = { id ->
                            scope.launch { app.container.searchHistoryRepository.deleteSearchEntry(id) }
                        },
                        onClearAll = {
                            scope.launch { app.container.searchHistoryRepository.clearHistory(SearchModule.INVOICE) }
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                    InvoiceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = if (statusFilter == st) null else st },
                            label = { Text(st.label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Bills Found",
                        description = "Tap + to create a GST invoice, non-GST bill, estimate, or draft.",
                        actionLabel = "Create Invoice",
                        onAction = { onCreateInvoice(InvoiceDocumentType.TAX_INVOICE) }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { inv ->
                    InvoiceRowCard(
                        invoice = inv,
                        currency = currency,
                        hideAmounts = hide,
                        onClick = {
                            val trimmed = search.trim()
                            if (trimmed.length >= 2) {
                                scope.launch {
                                    app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                                }
                            }
                            if (inv.status == InvoiceStatus.DRAFT) {
                                onEditDraft(inv.id, inv.documentType)
                            } else {
                                onOpenInvoiceDetail(inv.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun InvoiceRowCard(
    invoice: Invoice,
    currency: String,
    hideAmounts: Boolean,
    onClick: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text("${invoice.invoiceNumber} • ${invoice.customerName}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${invoice.documentType.label} • ${DateUtils.formatDate(invoice.invoiceDateMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (invoice.balanceDuePaise > 0L && invoice.status != InvoiceStatus.DRAFT && invoice.status != InvoiceStatus.CANCELLED) {
                    Text(
                        "Due: ${com.example.core.Money.formatPaise(invoice.balanceDuePaise, currency, hideAmounts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.danger
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(invoice.grandTotalPaise, currency, hideAmounts)
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(
                    label = if (invoice.status == InvoiceStatus.DRAFT) "Draft (Tap to Edit)" else invoice.status.label,
                    containerColor = when (invoice.status) {
                        InvoiceStatus.PAID -> semantic.successContainer
                        InvoiceStatus.PARTIAL -> semantic.warningContainer
                        InvoiceStatus.CANCELLED -> semantic.dangerContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = when (invoice.status) {
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

@Composable
fun FilteredDocumentListScreen(
    title: String,
    documentTypes: Set<InvoiceDocumentType>?,
    createType: InvoiceDocumentType,
    onBack: () -> Unit,
    onCreate: (InvoiceDocumentType) -> Unit,
    onEditDraft: (Long, InvoiceDocumentType) -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val invoices by app.container.billingRepository.allInvoices.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false

    val recentSearches by app.container.searchHistoryRepository
        .observeRecentSearches(SearchModule.INVOICE)
        .collectAsStateWithLifecycle(emptyList())

    var search by remember { mutableStateOf("") }

    LaunchedEffect(search) {
        val trimmed = search.trim()
        if (trimmed.length >= 2) {
            delay(900L)
            app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
        }
    }

    val filtered = remember(invoices, documentTypes, search) {
        invoices.filter { inv ->
            val matchesType = documentTypes == null || inv.documentType in documentTypes
            val matchesSearch = search.isBlank() ||
                inv.invoiceNumber.contains(search, ignoreCase = true) ||
                inv.customerName.contains(search, ignoreCase = true)
            matchesType && matchesSearch
        }
    }

    Scaffold(
        topBar = { HisabPulseTopBar(title = title, onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onCreate(createType) }) {
                Icon(Icons.Default.Add, contentDescription = "Create $title")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SearchField(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "Search $title",
                    onSearchSubmit = {
                        val trimmed = search.trim()
                        if (trimmed.length >= 2) {
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                            }
                        }
                    }
                )
            }
            if (recentSearches.isNotEmpty()) {
                item {
                    RecentSearchHistoryBar(
                        history = recentSearches,
                        onSelectQuery = { selected ->
                            search = selected
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, selected)
                            }
                        },
                        onDeleteEntry = { id ->
                            scope.launch { app.container.searchHistoryRepository.deleteSearchEntry(id) }
                        },
                        onClearAll = {
                            scope.launch { app.container.searchHistoryRepository.clearHistory(SearchModule.INVOICE) }
                        }
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No $title Yet",
                        description = "Create a new ${createType.label.lowercase()} using the + button.",
                        actionLabel = "Create ${createType.label}",
                        onAction = { onCreate(createType) }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { inv ->
                    InvoiceRowCard(
                        invoice = inv,
                        currency = currency,
                        hideAmounts = hide,
                        onClick = {
                            val trimmed = search.trim()
                            if (trimmed.length >= 2) {
                                scope.launch {
                                    app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                                }
                            }
                            if (inv.status == InvoiceStatus.DRAFT) onEditDraft(inv.id, inv.documentType)
                            else onOpenDetail(inv.id)
                        }
                    )
                }
            }
        }
    }
}
