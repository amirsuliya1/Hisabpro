package com.example.ui.screens.intelligence

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.entities.Product
import com.example.data.local.entities.SearchModule
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.RecentSearchHistoryBar
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PriceHistoryScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val entries by app.container.intelligenceRepository.priceHistory.collectAsStateWithLifecycle(emptyList())
    val recentSearches by app.container.searchHistoryRepository
        .observeRecentSearches(SearchModule.INVENTORY)
        .collectAsStateWithLifecycle(emptyList())
    var search by remember { mutableStateOf("") }

    LaunchedEffect(search) {
        val trimmed = search.trim()
        if (trimmed.length >= 2) {
            delay(900L)
            app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
        }
    }

    val filtered = remember(entries, search) {
        entries.filter {
            search.isBlank() || it.productName.contains(search, true) || it.changeSource.contains(search, true)
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Price History Tracking",
                subtitle = "${entries.size} recorded changes",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Search product or source",
                onSearchSubmit = {
                    val trimmed = search.trim()
                    if (trimmed.length >= 2) {
                        scope.launch {
                            app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
                        }
                    }
                }
            )
            RecentSearchHistoryBar(
                history = recentSearches,
                onSelectQuery = { selected ->
                    search = selected
                    scope.launch {
                        app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, selected)
                    }
                },
                onDeleteEntry = { id ->
                    scope.launch { app.container.searchHistoryRepository.deleteSearchEntry(id) }
                },
                onClearAll = {
                    scope.launch { app.container.searchHistoryRepository.clearHistory(SearchModule.INVENTORY) }
                }
            )
            if (filtered.isEmpty()) {
                EmptyStateCard(
                    title = "No Price History Yet",
                    description = "Price snapshots are captured automatically whenever you add products, update prices, bulk-reprice, or save purchase bills."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { row ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    StatusPill(
                                        label = row.changeSource.replace("_", " "),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Purchase: ${Money.formatPaise(row.purchasePricePaise, currency)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Selling: ${Money.formatPaise(row.sellingPricePaise, currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("MRP: ${Money.formatPaise(row.mrpPaise, currency)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    buildString {
                                        append(DateUtils.formatDateTime(row.changedAtMillis))
                                        if (row.supplierName.isNotBlank()) append(" • Supplier: ${row.supplierName}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchExpiryScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val batches by app.container.intelligenceRepository.productBatches.collectAsStateWithLifecycle(emptyList())
    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val productsMap = remember(products) { products.associateBy { it.id } }

    var filterBucket by remember { mutableStateOf("ALL") }
    var showAddBatch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        app.container.intelligenceRepository.refreshExpiryAlerts()
    }

    val now = remember(batches) { System.currentTimeMillis() }
    val filteredBatches = remember(batches, filterBucket, now) {
        batches.filter { b ->
            val exp = b.expiryDateMillis
            val days = if (exp != null) (exp - now) / 86_400_000L else Long.MAX_VALUE
            when (filterBucket) {
                "EXPIRED" -> exp != null && exp < now && b.remainingQuantityScaled > 0L
                "WITHIN_7D" -> exp != null && exp >= now && days <= 7L && b.remainingQuantityScaled > 0L
                "WITHIN_30D" -> exp != null && exp >= now && days <= 30L && b.remainingQuantityScaled > 0L
                "WITHIN_60D" -> exp != null && exp >= now && days <= 60L && b.remainingQuantityScaled > 0L
                else -> true
            }
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Batch & Expiry Tracking",
                subtitle = "FEFO ordering & expiry alerts",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val list = app.container.intelligenceRepository.refreshExpiryAlerts()
                            snackbar.showSnackbar("Scanned batches: ${list.size} active expiry alert(s)")
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan expiry alerts")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBatch = true },
                modifier = Modifier.testTag("add_batch_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add product batch")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${batches.size})",
                    "EXPIRED" to "Expired",
                    "WITHIN_7D" to "≤ 7 Days",
                    "WITHIN_30D" to "≤ 30 Days",
                    "WITHIN_60D" to "≤ 60 Days"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = filterBucket == key,
                        onClick = { filterBucket = key },
                        label = { Text(label) }
                    )
                }
            }

            if (filteredBatches.isEmpty()) {
                EmptyStateCard(
                    title = "No Batches in This View",
                    description = "Tap + to add batch numbers, manufacturing/expiry dates, or enable Medical Batch & Expiry in Settings."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredBatches, key = { it.id }) { batch ->
                        val p = productsMap[batch.productId]
                        val exp = batch.expiryDateMillis
                        val days = if (exp != null) (exp - now) / 86_400_000L else null
                        val (badgeText, bg, fg) = when {
                            exp == null -> Triple("No Expiry", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            exp < now -> Triple("EXPIRED", semantic.dangerContainer, semantic.danger)
                            days != null && days <= 7L -> Triple("${days}d left", semantic.dangerContainer, semantic.danger)
                            days != null && days <= 30L -> Triple("${days}d left", semantic.warningContainer, semantic.warning)
                            else -> Triple("${days}d left", semantic.successContainer, semantic.success)
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p?.name ?: "Product #${batch.productId}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Batch: ${batch.batchNumber}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    StatusPill(badgeText, bg, fg)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Remaining: ${Money.formatQty(Money.fromPaise(batch.remainingQuantityScaled))} / ${Money.formatQty(Money.fromPaise(batch.initialQuantityScaled))}", style = MaterialTheme.typography.bodySmall)
                                    if (exp != null) {
                                        Text("Exp: ${DateUtils.formatDate(exp)}", style = MaterialTheme.typography.bodySmall, color = fg)
                                    }
                                }
                                if (batch.supplierName.isNotBlank()) {
                                    Text("Supplier: ${batch.supplierName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBatch) {
        var selectedProduct by remember { mutableStateOf<Product?>(products.firstOrNull { !it.isServiceItem }) }
        var prodMenuExpanded by remember { mutableStateOf(false) }
        var batchNumber by remember { mutableStateOf("") }
        var qtyText by remember { mutableStateOf("10") }
        var expiryText by remember { mutableStateOf("") } // YYYY-MM-DD
        var supplierName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddBatch = false },
            title = { Text("Add Product Batch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = prodMenuExpanded,
                        onExpandedChange = { prodMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProduct?.name ?: "Select Product",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Product") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = prodMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = prodMenuExpanded,
                            onDismissRequest = { prodMenuExpanded = false }
                        ) {
                            products.filter { !it.isServiceItem }.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text(prod.name) },
                                    onClick = {
                                        selectedProduct = prod
                                        prodMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it.uppercase() },
                        label = { Text("Batch Number *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expiryText,
                        onValueChange = { expiryText = it },
                        label = { Text("Expiry Date (YYYY-MM-DD)") },
                        placeholder = { Text("2027-12-31") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supplierName,
                        onValueChange = { supplierName = it },
                        label = { Text("Supplier Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedProduct != null && batchNumber.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val prod = selectedProduct!!
                            val expMillis = if (expiryText.isNotBlank()) {
                                runCatching {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(expiryText.trim())?.time
                                }.getOrNull()
                            } else null
                            app.container.intelligenceRepository.addProductBatch(
                                productId = prod.id,
                                batchNumber = batchNumber,
                                expiryDateMillis = expMillis,
                                manufacturingDateMillis = null,
                                quantityScaled = Money.safeToPaise(Money.of(qtyText)),
                                unitCostPaise = prod.purchasePricePaise,
                                supplierName = supplierName
                            ).onSuccess {
                                showAddBatch = false
                                snackbar.showSnackbar("Batch $batchNumber added")
                            }.onFailure {
                                snackbar.showSnackbar(it.message ?: "Failed to add batch")
                            }
                        }
                    }
                ) { Text("Save Batch") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddBatch = false }) { Text("Cancel") } }
        )
    }
}
