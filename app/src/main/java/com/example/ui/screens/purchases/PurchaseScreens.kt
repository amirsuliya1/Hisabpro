package com.example.ui.screens.purchases

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.core.DateUtils
import com.example.core.GstCalculator
import com.example.core.GstSupplyType
import com.example.core.LineCalculationInput
import com.example.core.Money
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PurchaseDocumentType
import com.example.data.local.entities.PurchaseStatus
import com.example.data.local.entities.Supplier
import com.example.data.repository.PurchaseLineDraft
import com.example.data.repository.SavePurchaseRequest
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun PurchasesScreen(
    onlyReturns: Boolean = false,
    onBack: () -> Unit,
    onCreatePurchase: (Boolean) -> Unit,
    onOpenPurchaseDetail: (Long) -> Unit,
    onOpenPurchaseReturns: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val purchases by app.container.billingRepository.allPurchases.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false

    var search by remember { mutableStateOf("") }
    val filtered = remember(purchases, onlyReturns, search) {
        purchases.filter { p ->
            val typeOk = if (onlyReturns) p.documentType != PurchaseDocumentType.PURCHASE_INVOICE else true
            val qOk = search.isBlank() || p.purchaseNumber.contains(search, true) || p.supplierName.contains(search, true)
            typeOk && qOk
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = if (onlyReturns) "Purchase Returns" else "Purchases",
                onBack = onBack,
                actions = {
                    if (!onlyReturns) {
                        AssistChip(
                            onClick = onOpenPurchaseReturns,
                            label = { Text("Returns") },
                            leadingIcon = { Icon(Icons.Default.AssignmentReturn, null) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onCreatePurchase(onlyReturns) }) {
                Icon(Icons.Default.Add, contentDescription = "Create Purchase")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SearchField(search, { search = it }, "Search purchase # or supplier") }
            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = if (onlyReturns) "No Purchase Returns" else "No Purchases Recorded",
                        description = "Record supplier purchases to automatically increase stock, track input GST, and update payables.",
                        actionLabel = if (onlyReturns) "Record Return" else "Add Purchase",
                        onAction = { onCreatePurchase(onlyReturns) }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { p ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenPurchaseDetail(p.id) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${p.purchaseNumber} • ${p.supplierName}", style = MaterialTheme.typography.titleMedium)
                                Text("${p.documentType.label} • ${DateUtils.formatDate(p.purchaseDateMillis)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(p.grandTotalPaise, currency, hide)
                                StatusPill(p.status.label, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePurchaseScreen(
    isReturn: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val suppliers by app.container.partyRepository.suppliers.collectAsStateWithLifecycle(emptyList())
    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
    var supplierBillNo by remember { mutableStateOf("") }
    var docType by remember {
        mutableStateOf(if (isReturn) PurchaseDocumentType.PURCHASE_RETURN else PurchaseDocumentType.PURCHASE_INVOICE)
    }
    var isInterState by remember { mutableStateOf(false) }
    var additionalCharges by remember { mutableStateOf("0") }
    var paidAmount by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var notes by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<PurchaseLineDraft>() }

    var showProductPicker by remember { mutableStateOf(false) }

    val calc = GstCalculator.calculateDocument(
        lines = items.map { LineCalculationInput(it.quantity, it.rate, it.discountPercent, taxRatePercent = it.taxRatePercent) },
        supplyType = if (isInterState) GstSupplyType.INTER_STATE else GstSupplyType.INTRA_STATE,
        additionalCharges = Money.of(additionalCharges),
        enableRoundOff = false
    )

    Scaffold(
        topBar = { HisabPulseTopBar(title = if (isReturn) "Purchase Return" else "New Purchase Entry", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PurchaseDocumentType.entries.forEach { t ->
                        FilterChip(selected = docType == t, onClick = { docType = t }, label = { Text(t.label) })
                    }
                }
            }
            item {
                Text("Supplier", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedSupplier == null, onClick = { selectedSupplier = null }, label = { Text("Cash Supplier") })
                    suppliers.forEach { s ->
                        FilterChip(selected = selectedSupplier?.id == s.id, onClick = { selectedSupplier = s }, label = { Text(s.name) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = supplierBillNo, onValueChange = { supplierBillNo = it }, label = { Text("Supplier Bill / Invoice #") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inter-State Purchase (IGST)")
                    Switch(checked = isInterState, onCheckedChange = { isInterState = it })
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Items (${items.size})", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showProductPicker = true }) { Text("+ Add Product") }
                }
            }
            itemsIndexed(items) { idx, item ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.productName, fontWeight = FontWeight.SemiBold)
                            Text("${Money.formatQty(item.quantity)} ${item.unitName} x ${Money.format(item.rate, currency)} • GST ${Money.formatQty(item.taxRatePercent)}%")
                        }
                        IconButton(onClick = { items.removeAt(idx) }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = additionalCharges, onValueChange = { additionalCharges = it }, label = { Text("Additional Charges") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Text("Grand Total: ${Money.format(calc.grandTotal, currency)}", style = MaterialTheme.typography.titleLarge)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaymentMode.entries.forEach { m -> FilterChip(selected = paymentMode == m, onClick = { paymentMode = m }, label = { Text(m.label) }) }
                        }
                        OutlinedTextField(value = paidAmount, onValueChange = { paidAmount = it }, label = { Text("Amount Paid Now ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            app.container.billingRepository.savePurchase(
                                SavePurchaseRequest(
                                    supplierId = selectedSupplier?.id,
                                    supplierName = selectedSupplier?.name ?: "Cash Supplier",
                                    supplierGstin = selectedSupplier?.gstin ?: "",
                                    supplierBillNumber = supplierBillNo,
                                    documentType = docType,
                                    isInterState = isInterState,
                                    items = items.toList(),
                                    additionalCharges = Money.of(additionalCharges),
                                    paidAmount = Money.of(paidAmount),
                                    paymentMode = paymentMode,
                                    notes = notes
                                )
                            ).onSuccess { onSaved(it) }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Error saving purchase") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Save Purchase & Update Stock") }
            }
        }
    }

    if (showProductPicker) {
        var qty by remember { mutableStateOf("1") }
        var rate by remember { mutableStateOf("") }
        var selectedProd by remember { mutableStateOf(products.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { showProductPicker = false },
            title = { Text("Add Purchase Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        products.forEach { p ->
                            FilterChip(
                                selected = selectedProd?.id == p.id,
                                onClick = {
                                    selectedProd = p
                                    rate = Money.formatPlain(Money.fromPaise(p.purchasePricePaise))
                                },
                                label = { Text(p.name) }
                            )
                        }
                    }
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Purchase Rate") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = selectedProd ?: return@Button
                    items.add(
                        PurchaseLineDraft(
                            productId = p.id,
                            productName = p.name,
                            hsnSacCode = p.hsnSacCode,
                            unitName = p.unitName,
                            quantity = Money.of(qty).max(BigDecimal("0.01")),
                            rate = Money.of(rate),
                            taxRatePercent = Money.fromPaise(p.taxRatePercentScaled)
                        )
                    )
                    showProductPicker = false
                }) { Text("Add") }
            },
            dismissButton = { OutlinedButton(onClick = { showProductPicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun PurchaseDetailScreen(
    purchaseId: Long,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val purchase by app.container.billingRepository.observePurchase(purchaseId).collectAsStateWithLifecycle(null)
    val items by app.container.billingRepository.observePurchaseItems(purchaseId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var showPay by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HisabPulseTopBar(title = purchase?.purchaseNumber ?: "Purchase Details", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val p = purchase ?: return@Scaffold
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.supplierName, style = MaterialTheme.typography.titleLarge)
                        Text("${p.documentType.label} • ${DateUtils.formatDate(p.purchaseDateMillis)}")
                        Text("Grand Total: ${Money.formatPaise(p.grandTotalPaise, currency)}", fontWeight = FontWeight.Bold)
                        Text("Paid: ${Money.formatPaise(p.paidAmountPaise, currency)} • Due: ${Money.formatPaise(p.balanceDuePaise, currency)}")
                    }
                }
            }
            if (p.balanceDuePaise > 0L && p.supplierId != null && p.status != PurchaseStatus.CANCELLED) {
                item {
                    Button(onClick = { showPay = true }, modifier = Modifier.fillMaxWidth()) { Text("Record Supplier Payment") }
                }
            }
            items(items, key = { it.id }) { item ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(item.productName, fontWeight = FontWeight.SemiBold)
                            Text("${Money.formatQty(Money.fromPaise(item.quantityScaled))} ${item.unitName} x ${Money.formatPaise(item.ratePaise, currency)}")
                        }
                        MoneyText(item.lineTotalPaise, currency)
                    }
                }
            }
        }
    }

    if (showPay && purchase?.supplierId != null) {
        var amt by remember { mutableStateOf(Money.formatPlain(Money.fromPaise(purchase!!.balanceDuePaise))) }
        AlertDialog(
            onDismissRequest = { showPay = false },
            title = { Text("Pay Supplier Balance") },
            text = { OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.accountingRepository.recordSupplierPayment(
                            supplierId = purchase!!.supplierId!!,
                            amountPaise = Money.safeToPaise(Money.of(amt)),
                            paymentMode = PaymentMode.CASH,
                            purchaseIdToAllocate = purchase!!.id
                        ).onSuccess { showPay = false; snackbar.showSnackbar("Payment recorded") }
                    }
                }) { Text("Pay") }
            },
            dismissButton = { OutlinedButton(onClick = { showPay = false }) { Text("Cancel") } }
        )
    }
}
