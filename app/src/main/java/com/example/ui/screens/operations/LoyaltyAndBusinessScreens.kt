package com.example.ui.screens.operations

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.LoyaltyConfiguration
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch

@Composable
fun LoyaltyProgramScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val config by app.container.engagementRepository.loyaltyConfig.collectAsStateWithLifecycle(null)
    val accounts by app.container.engagementRepository.loyaltyAccounts.collectAsStateWithLifecycle(emptyList())
    val txns by app.container.engagementRepository.loyaltyTransactions.collectAsStateWithLifecycle(emptyList())
    val customers by app.container.partyRepository.customers.collectAsStateWithLifecycle(emptyList())
    val customerMap = remember(customers) { customers.associateBy { it.id } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var adjustCustomerId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Loyalty & Rewards Program",
                subtitle = "Customer points, tiers & redemptions",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Customers (${accounts.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("History (${txns.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Rules") })
            }

            when (selectedTab) {
                0 -> {
                    val activeCustomers = customers.filter { it.isActive }
                    val accountByCust = accounts.associateBy { it.customerId }
                    if (activeCustomers.isEmpty()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                title = "No Customers Enrolled Yet",
                                description = "Add customers under Parties; finalized invoices earn points automatically when Loyalty is enabled."
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeCustomers, key = { it.id }) { cust ->
                                val acc = accountByCust[cust.id]
                                val bal = acc?.pointsBalance ?: 0L
                                val tier = acc?.membershipTier?.label ?: "Silver"
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(cust.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                StatusPill(tier, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                            Text(
                                                "Earned: ${acc?.lifetimePointsEarned ?: 0} • Redeemed: ${acc?.lifetimePointsRedeemed ?: 0}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("$bal pts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            OutlinedButton(
                                                onClick = { adjustCustomerId = cust.id },
                                                modifier = Modifier.testTag("adjust_loyalty_${cust.id}")
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = null)
                                                Spacer(Modifier.padding(2.dp))
                                                Text("Adjust")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (txns.isEmpty()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                title = "No Point Transactions Yet",
                                description = "Points earned on sales, redemptions, and manual adjustments appear here."
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(txns, key = { it.id }) { t ->
                                val positive = t.pointsDelta >= 0L
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(customerMap[t.customerId]?.name ?: "Customer #${t.customerId}", fontWeight = FontWeight.Bold)
                                            Text("${t.txnType.label} • ${t.notes}", style = MaterialTheme.typography.bodySmall)
                                            Text(DateUtils.formatDateTime(t.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "${if (positive) "+" else ""}${t.pointsDelta} pts",
                                                fontWeight = FontWeight.Bold,
                                                color = if (positive) semantic.success else semantic.danger
                                            )
                                            Text("Bal: ${t.balanceAfter}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val cfg = config ?: LoyaltyConfiguration()
                    var enabled by remember(cfg) { mutableStateOf(cfg.isEnabled) }
                    var rupeesPerPt by remember(cfg) { mutableStateOf(cfg.rupeesPerPoint.toString()) }
                    var ptValueRupees by remember(cfg) { mutableStateOf(Money.formatPlain(Money.fromPaise(cfg.pointValuePaise))) }
                    var minRedeem by remember(cfg) { mutableStateOf(cfg.minRedeemPoints.toString()) }
                    var maxRedeem by remember(cfg) { mutableStateOf(cfg.maxRedeemPointsPerInvoice.toString()) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Loyalty, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text("Enable Loyalty Program", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                                    }
                                    OutlinedTextField(
                                        value = rupeesPerPt,
                                        onValueChange = { rupeesPerPt = it.filter(Char::isDigit) },
                                        label = { Text("Spend ₹ per 1 Point Earned") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = ptValueRupees,
                                        onValueChange = { ptValueRupees = it },
                                        label = { Text("1 Point Redemption Value (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = minRedeem,
                                            onValueChange = { minRedeem = it.filter(Char::isDigit) },
                                            label = { Text("Min Redeem Pts") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = maxRedeem,
                                            onValueChange = { maxRedeem = it.filter(Char::isDigit) },
                                            label = { Text("Max Pts / Bill") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Text("Tiers: Silver (0+ pts • 1.0x), Gold (500+ pts • 1.25x), Platinum (2000+ pts • 1.5x)", style = MaterialTheme.typography.labelSmall)
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                app.container.engagementRepository.saveLoyaltyConfig(
                                                    cfg.copy(
                                                        isEnabled = enabled,
                                                        rupeesPerPoint = (rupeesPerPt.toLongOrNull() ?: 100L).coerceAtLeast(1L),
                                                        pointValuePaise = Money.safeToPaise(Money.of(ptValueRupees)).coerceAtLeast(10L),
                                                        minRedeemPoints = (minRedeem.toLongOrNull() ?: 50L).coerceAtLeast(1L),
                                                        maxRedeemPointsPerInvoice = (maxRedeem.toLongOrNull() ?: 500L).coerceAtLeast(1L)
                                                    )
                                                )
                                                snackbar.showSnackbar("Loyalty rules updated")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("save_loyalty_rules_btn")
                                    ) { Text("Save Loyalty Rules") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adjustCustomerId != null) {
        val cust = customerMap[adjustCustomerId]
        var deltaText by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("Manual bonus / adjustment") }
        AlertDialog(
            onDismissRequest = { adjustCustomerId = null },
            title = { Text("Adjust Points • ${cust?.name ?: ""}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter positive points to award or negative (e.g. -50) to deduct. Balance cannot go negative.")
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        label = { Text("Points (+/-)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason / Notes") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val delta = deltaText.trim().toLongOrNull()
                    if (delta == null || delta == 0L) {
                        scope.launch { snackbar.showSnackbar("Enter a non-zero integer") }
                        return@Button
                    }
                    scope.launch {
                        app.container.engagementRepository.adjustLoyaltyPointsManually(adjustCustomerId!!, delta, reason)
                            .onSuccess {
                                adjustCustomerId = null
                                snackbar.showSnackbar("Loyalty points updated")
                            }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Adjustment rejected") }
                    }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { adjustCustomerId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun BusinessSwitcherScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val profiles by app.container.businessRepository.allBusinessProfiles.collectAsStateWithLifecycle(emptyList())
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val activeBizId = settings?.activeBusinessId ?: 1L
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Multi-Business Profiles",
                subtitle = "Isolated catalogs, parties & double-entry books",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("add_business_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create new business")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(profiles, key = { it.id }) { prof ->
                val isSelected = prof.id == activeBizId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.medium
                        )
                        .clickable {
                            if (!isSelected) {
                                scope.launch {
                                    app.container.businessRepository.switchActiveBusiness(prof.id)
                                        .onSuccess { snackbar.showSnackbar("Switched to ${prof.businessName}") }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Switch failed") }
                                }
                            }
                        }
                        .testTag("business_profile_${prof.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(prof.businessName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${prof.category.label} • ${if (prof.gstin.isNotBlank()) "GSTIN ${prof.gstin}" else "Unregistered"} • Prefix ${prof.invoicePrefix}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active Business", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var owner by remember { mutableStateOf("") }
        var gstin by remember { mutableStateOf("") }
        var prefix by remember { mutableStateOf("BIZ-") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Business Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Creates a completely separate accounting book, catalog, party list, and invoice series.")
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business Name *") }, singleLine = true)
                    OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner Name") }, singleLine = true)
                    OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN (Optional)") }, singleLine = true)
                    OutlinedTextField(value = prefix, onValueChange = { prefix = it.uppercase() }, label = { Text("Invoice Prefix") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            app.container.businessRepository.createBusinessProfile(
                                BusinessProfile(
                                    id = 0L,
                                    businessName = name.trim(),
                                    ownerName = owner.trim(),
                                    gstin = gstin.trim(),
                                    invoicePrefix = prefix.ifBlank { "INV-" }
                                ),
                                switchToNewBusiness = true
                            )
                            showCreateDialog = false
                            snackbar.showSnackbar("Created and switched to ${name.trim()}")
                        }
                    }
                ) { Text("Create & Switch") }
            },
            dismissButton = { OutlinedButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }
}
