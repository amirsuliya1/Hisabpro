package com.example.ui.screens.items

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.entities.SearchModule
import com.example.data.local.entities.Warehouse
import com.example.data.repository.InventoryRepository
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.RecentSearchHistoryBar
import com.example.ui.components.SearchField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    onOpenStockAdjustment: (Long?) -> Unit,
    onOpenStockTransfer: (Long?) -> Unit,
    onOpenProductDetail: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val lowStock by app.container.inventoryRepository.lowStockProducts.collectAsStateWithLifecycle(emptyList())
    val outOfStock by app.container.inventoryRepository.outOfStockProducts.collectAsStateWithLifecycle(emptyList())
    val warehouses by app.container.businessRepository.warehouses.collectAsStateWithLifecycle(emptyList())
    val movements by app.container.inventoryRepository.recentMovements.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    val recentSearches by app.container.searchHistoryRepository
        .observeRecentSearches(SearchModule.INVENTORY)
        .collectAsStateWithLifecycle(emptyList())

    var search by remember { mutableStateOf("") }
    var showAddWarehouse by remember { mutableStateOf(false) }
    var showProductionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(search) {
        val trimmed = search.trim()
        if (trimmed.length >= 2) {
            delay(900L)
            app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
        }
    }

    val totalValuation = remember(products) {
        products.filter { !it.isServiceItem }.sumOf {
            InventoryRepository.computeStockValuePaise(it.currentStockScaled, it.purchasePricePaise)
        }
    }
    val productNamesById = remember(products) { products.associate { it.id to it.name } }
    val filteredAlerts = remember(outOfStock, lowStock, search) {
        val allAlerts = outOfStock + lowStock
        if (search.isBlank()) allAlerts else allAlerts.filter { p ->
            p.name.contains(search, true) || p.sku.contains(search, true) || p.barcode.contains(search, true)
        }
    }
    val filteredMovements = remember(movements, productNamesById, search) {
        if (search.isBlank()) movements else movements.filter { m ->
            val pName = productNamesById[m.productId].orEmpty()
            pName.contains(search, true) || m.movementType.label.contains(search, true) || m.notes.contains(search, true)
        }
    }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Inventory & Stock", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Total Stock Valuation", style = MaterialTheme.typography.titleMedium)
                        MoneyText(totalValuation, currency, settings?.hideAmounts ?: false, fontSizeSp = 22)
                        Text("Low Stock: ${lowStock.size} • Out of Stock: ${outOfStock.size} • Warehouses: ${warehouses.size}")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenStockAdjustment(null) }) {
                        Icon(Icons.Default.Tune, null)
                        Text(" Adjust")
                    }
                    OutlinedButton(onClick = { onOpenStockTransfer(null) }) {
                        Icon(Icons.Default.SwapHoriz, null)
                        Text(" Transfer")
                    }
                    OutlinedButton(onClick = { showAddWarehouse = true }) {
                        Icon(Icons.Default.Warehouse, null)
                        Text(" Add Warehouse")
                    }
                    if (settings?.enableManufacturingModule == true) {
                        OutlinedButton(onClick = { showProductionDialog = true }) {
                            Icon(Icons.Default.Build, null)
                            Text(" Production")
                        }
                    }
                }
            }

            item {
                SearchField(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "Search stock alerts or movements",
                    onSearchSubmit = {
                        val trimmed = search.trim()
                        if (trimmed.length >= 2) {
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
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
                }
            }

            if (filteredAlerts.isNotEmpty()) {
                item { Text("Stock Alerts", style = MaterialTheme.typography.titleMedium) }
                items(filteredAlerts.distinctBy { it.id }, key = { "alert_${it.id}" }) { p ->
                    Card(modifier = Modifier.fillMaxWidth().clickable {
                        val trimmed = search.trim()
                        if (trimmed.length >= 2) {
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
                            }
                        }
                        onOpenProductDetail(p.id)
                    }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text("Min required: ${Money.formatQty(Money.fromPaise(p.minStockScaled))} ${p.unitName}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                "${Money.formatQty(Money.fromPaise(p.currentStockScaled))} ${p.unitName}",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { Text("Recent Stock Movements", style = MaterialTheme.typography.titleMedium) }
            if (filteredMovements.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = if (search.isBlank()) "No Stock Movements Yet" else "No Matching Movements",
                        description = if (search.isBlank()) {
                            "Opening stock, sales, purchases, adjustments, and transfers appear here automatically."
                        } else {
                            "No stock alerts or movements matched \"$search\"."
                        }
                    )
                }
            } else {
                items(filteredMovements.take(40), key = { "mov_${it.id}" }) { m ->
                    val prodName = productNamesById[m.productId] ?: "Product #${m.productId}"
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$prodName • ${m.movementType.label}", fontWeight = FontWeight.Medium)
                                Text("${DateUtils.formatDateTime(m.createdAt)} • ${m.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val sign = if (m.quantityDeltaScaled >= 0) "+" else ""
                            Text("$sign${Money.formatQty(Money.fromPaise(m.quantityDeltaScaled))}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAddWarehouse) {
        var whName by remember { mutableStateOf("") }
        var whLoc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddWarehouse = false },
            title = { Text("Add Warehouse") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = whName, onValueChange = { whName = it }, label = { Text("Warehouse Name") })
                    OutlinedTextField(value = whLoc, onValueChange = { whLoc = it }, label = { Text("Location") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (whName.isNotBlank()) {
                        scope.launch {
                            app.container.businessRepository.saveWarehouse(Warehouse(name = whName.trim(), location = whLoc.trim()))
                            showAddWarehouse = false
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddWarehouse = false }) { Text("Cancel") } }
        )
    }

    if (showProductionDialog && products.size >= 2) {
        var rawIdx by remember { mutableStateOf(0) }
        var finIdx by remember { mutableStateOf(1) }
        var rawQty by remember { mutableStateOf("1") }
        var finQty by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { showProductionDialog = false },
            title = { Text("Manufacturing Stock Conversion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Raw Material: ${products[rawIdx].name}", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        products.forEachIndexed { idx, p -> FilterChip(selected = rawIdx == idx, onClick = { rawIdx = idx }, label = { Text(p.name) }) }
                    }
                    OutlinedTextField(value = rawQty, onValueChange = { rawQty = it }, label = { Text("Raw Qty Consumed") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("Finished Goods: ${products[finIdx].name}", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        products.forEachIndexed { idx, p -> FilterChip(selected = finIdx == idx, onClick = { finIdx = idx }, label = { Text(p.name) }) }
                    }
                    OutlinedTextField(value = finQty, onValueChange = { finQty = it }, label = { Text("Finished Qty Produced") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.inventoryRepository.recordManufacturingConversion(
                            rawMaterialId = products[rawIdx].id,
                            rawQtyScaled = Money.safeToPaise(Money.of(rawQty)),
                            finishedProductId = products[finIdx].id,
                            finishedQtyScaled = Money.safeToPaise(Money.of(finQty))
                        ).onSuccess {
                            showProductionDialog = false
                            snackbar.showSnackbar("Production batch recorded")
                        }.onFailure { snackbar.showSnackbar(it.message ?: "Conversion failed") }
                    }
                }) { Text("Convert") }
            },
            dismissButton = { OutlinedButton(onClick = { showProductionDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StockAdjustmentScreen(
    presetProductId: Long?,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())

    var selectedId by remember(presetProductId, products) { mutableStateOf(presetProductId ?: products.firstOrNull()?.id) }
    var isIncrease by remember { mutableStateOf(true) }
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("Physical count adjustment") }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Stock Adjustment", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Select Product", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                products.forEach { p ->
                    FilterChip(selected = selectedId == p.id, onClick = { selectedId = p.id }, label = { Text(p.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = isIncrease, onClick = { isIncrease = true }, label = { Text("Add Stock (+)") })
                FilterChip(selected = !isIncrease, onClick = { isIncrease = false }, label = { Text("Reduce Stock (-)") })
            }
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Reason / Notes") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val id = selectedId ?: return@Button
                    val rawScaled = Money.safeToPaise(Money.of(qtyText))
                    val delta = if (isIncrease) rawScaled else -rawScaled
                    scope.launch {
                        app.container.inventoryRepository.adjustStock(id, delta, notes = notes)
                            .onSuccess { onBack() }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                    }
                },
                enabled = selectedId != null,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Apply Adjustment") }
        }
    }
}

@Composable
fun StockTransferScreen(
    presetProductId: Long?,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val warehouses by app.container.businessRepository.warehouses.collectAsStateWithLifecycle(emptyList())

    var selectedProductId by remember(presetProductId, products) { mutableStateOf(presetProductId ?: products.firstOrNull()?.id) }
    var fromId by remember(warehouses) { mutableStateOf(warehouses.firstOrNull()?.id) }
    var toId by remember(warehouses) { mutableStateOf(warehouses.getOrNull(1)?.id ?: warehouses.firstOrNull()?.id) }
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = { HisabPulseTopBar(title = "Stock Transfer", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Product", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                products.forEach { p -> FilterChip(selected = selectedProductId == p.id, onClick = { selectedProductId = p.id }, label = { Text(p.name) }) }
            }
            Text("From Warehouse", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                warehouses.forEach { w -> FilterChip(selected = fromId == w.id, onClick = { fromId = w.id }, label = { Text(w.name) }) }
            }
            Text("To Warehouse", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                warehouses.forEach { w -> FilterChip(selected = toId == w.id, onClick = { toId = w.id }, label = { Text(w.name) }) }
            }
            OutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Transfer Notes") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val pId = selectedProductId ?: return@Button
                    val f = fromId ?: return@Button
                    val t = toId ?: return@Button
                    scope.launch {
                        app.container.inventoryRepository.transferStock(pId, f, t, Money.safeToPaise(Money.of(qtyText)), notes)
                            .onSuccess { onBack() }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Transfer failed") }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Record Stock Transfer") }
        }
    }
}
