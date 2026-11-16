package com.example.ui.screens.intelligence

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.core.Money
import com.example.data.local.entities.SearchModule
import com.example.data.repository.StockPredictionItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.RecentSearchHistoryBar
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StockPredictionScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    val recentSearches by app.container.searchHistoryRepository
        .observeRecentSearches(SearchModule.INVENTORY)
        .collectAsStateWithLifecycle(emptyList())

    var items by remember { mutableStateOf<List<StockPredictionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var filterClass by remember { mutableStateOf("ALL") }
    var editingItem by remember { mutableStateOf<StockPredictionItem?>(null) }

    LaunchedEffect(search) {
        val trimmed = search.trim()
        if (trimmed.length >= 2) {
            delay(900L)
            app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            items = app.container.intelligenceRepository.computeStockPredictions()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filtered = items.filter { item ->
        val matchesSearch = search.isBlank() || item.product.name.contains(search, true)
        val matchesFilter = filterClass == "ALL" || item.velocityClass == filterClass
        matchesSearch && matchesFilter
    }

    val fastCount = items.count { it.velocityClass == "FAST" }
    val lowCoverCount = items.count { it.estimatedDaysRemaining in 0..7 }
    val reorderNeededCount = items.count { it.suggestedReorderScaled > 0L }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Smart Stock Prediction",
                subtitle = "Offline demand & reorder intelligence",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refresh() }, modifier = Modifier.testTag("refresh_predictions_btn")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recalculate stock predictions")
                    }
                }
            )
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
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStatBox("Fast Moving", "$fastCount", Modifier.weight(1f))
                SummaryStatBox("≤7d Cover", "$lowCoverCount", Modifier.weight(1f))
                SummaryStatBox("Need Reorder", "$reorderNeededCount", Modifier.weight(1f))
            }
            SearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Search product velocity",
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${items.size})",
                    "FAST" to "Fast",
                    "NORMAL" to "Normal",
                    "SLOW" to "Slow",
                    "OVERSTOCK" to "Overstock",
                    "INSUFFICIENT_DATA" to "Needs History"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = filterClass == key,
                        onClick = { filterClass = key },
                        label = { Text(label) }
                    )
                }
            }

            if (!loading && filtered.isEmpty()) {
                EmptyStateCard(
                    title = "No Products to Forecast",
                    description = "Add catalog items and record finalized sales to calculate 7d/30d/90d run-rates and reorder suggestions."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.product.id }) { item ->
                        val (pillBg, pillFg) = when (item.velocityClass) {
                            "FAST" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            "NORMAL" -> semantic.successContainer to semantic.success
                            "SLOW" -> semantic.warningContainer to semantic.warning
                            "OVERSTOCK" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Stock: ${Money.formatQty(Money.fromPaise(item.product.currentStockScaled))} ${item.product.unitName} • ${item.saleEventCount} sales",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(label = item.velocityClass.replace("_", " "), containerColor = pillBg, contentColor = pillFg)
                                }
                                Text(item.explanation, style = MaterialTheme.typography.bodyMedium)
                                if (item.velocityClass != "INSUFFICIENT_DATA") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("7d: ${Money.formatQty(Money.fromPaise(item.avgDailySales7dScaled))}/d", style = MaterialTheme.typography.labelMedium)
                                        Text("30d: ${Money.formatQty(Money.fromPaise(item.avgDailySales30dScaled))}/d", style = MaterialTheme.typography.labelMedium)
                                        Text("90d: ${Money.formatQty(Money.fromPaise(item.avgDailySales90dScaled))}/d", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            if (item.suggestedReorderScaled > 0L) {
                                                val tag = if (item.manualOverrideScaled != null) " (Custom)" else " (Suggested)"
                                                "Reorder: ${Money.formatQty(Money.fromPaise(item.suggestedReorderScaled))} ${item.product.unitName}$tag"
                                            } else "No immediate reorder required",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (item.suggestedReorderScaled > 0L) MaterialTheme.colorScheme.primary else semantic.success
                                        )
                                        if (item.suggestedReorderScaled > 0L && item.product.purchasePricePaise > 0L) {
                                            val estCost = com.example.data.repository.InventoryRepository.computeStockValuePaise(
                                                item.suggestedReorderScaled,
                                                item.product.purchasePricePaise
                                            )
                                            Text(
                                                "Est. purchase budget: ${Money.formatPaise(estCost, currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { editingItem = item }) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                        Spacer(Modifier.padding(2.dp))
                                        Text("Override")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingItem != null) {
        val target = editingItem!!
        var qtyText by remember {
            mutableStateOf(Money.formatPlain(Money.fromPaise(target.manualOverrideScaled ?: target.suggestedReorderScaled)))
        }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Reorder Override • ${target.product.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a manual reorder target (${target.product.unitName}) or clear override to return to automated velocity formula.")
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Reorder Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val scaled = Money.safeToPaise(Money.of(qtyText))
                        app.container.intelligenceRepository.setManualReorderOverride(target.product.id, scaled)
                        editingItem = null
                        refresh()
                        snackbar.showSnackbar("Reorder override saved")
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (target.manualOverrideScaled != null) {
                        TextButton(onClick = {
                            scope.launch {
                                app.container.intelligenceRepository.setManualReorderOverride(target.product.id, null)
                                editingItem = null
                                refresh()
                            }
                        }) { Text("Clear") }
                    }
                    OutlinedButton(onClick = { editingItem = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun SummaryStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
