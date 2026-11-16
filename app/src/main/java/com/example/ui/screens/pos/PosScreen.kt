package com.example.ui.screens.pos

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.core.GstCalculator
import com.example.core.GstSupplyType
import com.example.core.LineCalculationInput
import com.example.core.Money
import com.example.data.local.entities.Customer
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Product
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.SaveInvoiceRequest
import com.example.ui.components.BarcodeScannerSheet
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.SearchField
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun PosScreen(
    onBack: () -> Unit,
    onInvoiceGenerated: (Long) -> Unit,
    onAddProduct: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val customers by app.container.partyRepository.customers.collectAsStateWithLifecycle(emptyList())
    val heldBills by app.container.billingRepository.heldPosBills.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val appSettings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val loyaltyConfig by app.container.engagementRepository.loyaltyConfig.collectAsStateWithLifecycle(null)
    val loyaltyAccounts by app.container.engagementRepository.loyaltyAccounts.collectAsStateWithLifecycle(emptyList())
    val currency = profile?.currencySymbol ?: "₹"

    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var activeHeldInvoiceId by remember { mutableStateOf<Long?>(null) }
    val cart = remember { mutableStateListOf<InvoiceLineDraft>() }
    var discountText by remember { mutableStateOf("0") }
    var redeemPointsText by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var splitCash by remember { mutableStateOf("") }
    var splitUpi by remember { mutableStateOf("") }
    var useSplitPayment by remember { mutableStateOf(false) }

    var showScanner by remember { mutableStateOf(false) }
    var showHeldDialog by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var editingCartIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var expiredBatchWarning by remember { mutableStateOf<String?>(null) }

    val categories = remember(products) {
        listOf("All") + products.map { it.categoryName }.distinct().sorted()
    }

    val filteredProducts = remember(products, selectedCategory, search) {
        products.filter { p ->
            val catMatch = selectedCategory == "All" || p.categoryName == selectedCategory
            val qMatch = search.isBlank() || p.name.contains(search, true) || p.barcode.contains(search, true) || p.sku.contains(search, true)
            catMatch && qMatch
        }
    }

    val selectedLoyaltyAccount = remember(selectedCustomer, loyaltyAccounts) {
        selectedCustomer?.let { c ->
            loyaltyAccounts.firstOrNull { it.customerId == c.id }
        }
    }
    val reqRedeemPts = redeemPointsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val validRedeemPts = if (
        loyaltyConfig?.isEnabled == true &&
        selectedLoyaltyAccount != null &&
        reqRedeemPts >= (loyaltyConfig?.minRedeemPoints ?: 0L)
    ) {
        reqRedeemPts
            .coerceAtMost(selectedLoyaltyAccount.pointsBalance)
            .coerceAtMost(loyaltyConfig?.maxRedeemPointsPerInvoice ?: 0L)
    } else 0L
    val loyaltyDiscountBd = Money.fromPaise(validRedeemPts * (loyaltyConfig?.pointValuePaise ?: 0L))

    val cartSnapshot = cart.toList()
    val effectiveInvoiceDiscount = Money.of(discountText).max(BigDecimal.ZERO)
    val docCalc = remember(cartSnapshot, profile?.isGstRegistered, effectiveInvoiceDiscount, loyaltyDiscountBd) {
        GstCalculator.calculateDocument(
            lines = cartSnapshot.map {
                LineCalculationInput(it.quantity, it.rate, it.discountPercent, it.discountFlat, it.taxRatePercent, it.isTaxInclusive)
            },
            supplyType = if (profile?.isGstRegistered == false) GstSupplyType.NON_GST else GstSupplyType.INTRA_STATE,
            invoiceDiscountFlat = effectiveInvoiceDiscount + loyaltyDiscountBd,
            enableRoundOff = appSettings?.enableRoundOff ?: true
        )
    }

    fun effectiveRateForProduct(p: Product, qty: BigDecimal): BigDecimal {
        val wholesaleEnabled = appSettings?.enableWholesaleModule == true &&
            p.wholesalePricePaise > 0L &&
            qty >= Money.fromPaise(p.wholesaleMinQtyScaled).max(BigDecimal.ONE)
        return Money.fromPaise(if (wholesaleEnabled) p.wholesalePricePaise else p.sellingPricePaise)
    }

    fun addToCart(p: Product) {
        scope.launch {
            val allowNeg = appSettings?.allowNegativeStock == true
            val available = Money.fromPaise(p.currentStockScaled)
            val idx = cart.indexOfFirst { it.productId == p.id }
            if (idx >= 0) {
                val cur = cart[idx]
                val nextQty = cur.quantity + BigDecimal.ONE
                if (!allowNeg && !p.isServiceItem && nextQty > available) {
                    snackbar.showSnackbar("Only ${Money.formatQty(available)} ${p.unitName} of ${p.name} in stock")
                    return@launch
                }
                cart[idx] = cur.copy(
                    quantity = nextQty,
                    rate = effectiveRateForProduct(p, nextQty)
                )
            } else {
                if (!allowNeg && !p.isServiceItem && available < BigDecimal.ONE) {
                    snackbar.showSnackbar("${p.name} is out of stock")
                    return@launch
                }
                val batches = app.container.intelligenceRepository
                    .observeBatchesForProduct(p.id)
                    .firstOrNull()
                    .orEmpty()
                val now = System.currentTimeMillis()
                val preferredBatch = batches.firstOrNull { it.expiryDateMillis == null || it.expiryDateMillis >= now }
                    ?: batches.firstOrNull()
                cart.add(
                    InvoiceLineDraft(
                        productId = p.id,
                        batchId = preferredBatch?.id,
                        productName = p.name,
                        productCode = p.productCode.ifBlank { p.sku },
                        hsnSacCode = p.hsnSacCode,
                        unitName = p.unitName,
                        quantity = BigDecimal.ONE,
                        rate = effectiveRateForProduct(p, BigDecimal.ONE),
                        purchaseCost = Money.fromPaise(preferredBatch?.unitCostPaise ?: p.purchasePricePaise),
                        taxRatePercent = Money.fromPaise(p.taxRatePercentScaled),
                        isTaxInclusive = p.isTaxInclusive,
                        batchNumber = preferredBatch?.batchNumber ?: p.batchNumber,
                        expiryDateMillis = preferredBatch?.expiryDateMillis ?: p.expiryDateMillis
                    )
                )
            }
        }
    }

    fun finalizePosCheckout(allowExpiredOverride: Boolean = false) {
        if (isSubmitting) return
        checkoutError = null
        val cashBd = Money.of(splitCash).max(BigDecimal.ZERO)
        val upiBd = Money.of(splitUpi).max(BigDecimal.ZERO)
        if (useSplitPayment && docCalc.grandTotal > BigDecimal.ZERO && (cashBd + upiBd) <= BigDecimal.ZERO) {
            checkoutError = "Enter Cash and/or UPI split payment amount"
            return
        }
        isSubmitting = true
        scope.launch {
            try {
                val paid = if (useSplitPayment) {
                    (cashBd + upiBd).min(docCalc.grandTotal)
                } else docCalc.grandTotal
                val splitNote = if (useSplitPayment) "Split Payment: Cash $cashBd, UPI $upiBd" else ""
                app.container.billingRepository.saveOrFinalizeInvoice(
                    SaveInvoiceRequest(
                        existingInvoiceId = activeHeldInvoiceId,
                        documentType = InvoiceDocumentType.RETAIL_INVOICE,
                        customerId = selectedCustomer?.id,
                        customerName = selectedCustomer?.name ?: "Walk-in Customer",
                        customerPhone = selectedCustomer?.phone ?: "",
                        customerAddress = selectedCustomer?.address ?: "",
                        customerGstin = selectedCustomer?.gstin ?: "",
                        items = cart.toList(),
                        invoiceDiscountFlat = effectiveInvoiceDiscount,
                        enableRoundOff = appSettings?.enableRoundOff ?: true,
                        paidAmount = paid,
                        paymentMode = if (useSplitPayment) PaymentMode.UPI else paymentMode,
                        redeemLoyaltyPoints = validRedeemPts,
                        allowExpiredBatchOverride = allowExpiredOverride,
                        notes = splitNote
                    )
                ).onSuccess { newId ->
                    cart.clear()
                    selectedCustomer = null
                    discountText = "0"
                    splitCash = ""
                    splitUpi = ""
                    useSplitPayment = false
                    redeemPointsText = ""
                    activeHeldInvoiceId = null
                    checkoutError = null
                    showCheckoutDialog = false
                    onInvoiceGenerated(newId)
                }.onFailure { err ->
                    val msg = err.message ?: "Checkout failed"
                    if (msg.startsWith("EXPIRED_BATCH_WARNING:")) {
                        expiredBatchWarning = msg.removePrefix("EXPIRED_BATCH_WARNING:").trim()
                    } else {
                        checkoutError = msg
                        snackbar.showSnackbar(msg)
                    }
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    val totalQty = cart.fold(BigDecimal.ZERO) { acc, line -> acc + line.quantity }
    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Counter POS",
                subtitle = "${Money.formatQty(totalQty)} items in cart",
                onBack = onBack,
                actions = {
                    if (heldBills.isNotEmpty()) {
                        TextButton(onClick = { showHeldDialog = true }) {
                            Icon(Icons.Default.PlayCircle, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Held (${heldBills.size})")
                        }
                    }
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    if (cart.isNotEmpty()) {
                        cart.forEachIndexed { idx, line ->
                            val matchedProd = products.firstOrNull { it.id == line.productId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingCartIndex = idx }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Text(line.productName, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                                    Icon(Icons.Default.Edit, contentDescription = "Edit line", modifier = Modifier.padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (line.quantity > BigDecimal.ONE) {
                                            val nextQty = line.quantity - BigDecimal.ONE
                                            cart[idx] = line.copy(
                                                quantity = nextQty,
                                                rate = matchedProd?.let { effectiveRateForProduct(it, nextQty) } ?: line.rate
                                            )
                                        } else {
                                            cart.removeAt(idx)
                                        }
                                    }) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
                                    Text(Money.formatQty(line.quantity), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val nextQty = line.quantity + BigDecimal.ONE
                                        val allowNeg = appSettings?.allowNegativeStock == true
                                        if (matchedProd != null && !allowNeg && !matchedProd.isServiceItem) {
                                            val avail = Money.fromPaise(matchedProd.currentStockScaled)
                                            if (nextQty > avail) {
                                                scope.launch { snackbar.showSnackbar("Only ${Money.formatQty(avail)} ${matchedProd.unitName} available") }
                                                return@IconButton
                                            }
                                        }
                                        cart[idx] = line.copy(
                                            quantity = nextQty,
                                            rate = matchedProd?.let { effectiveRateForProduct(it, nextQty) } ?: line.rate
                                        )
                                    }) { Icon(Icons.Default.Add, contentDescription = "Increase") }
                                }
                                Text(Money.format(line.rate * line.quantity, currency), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total (incl. GST)", style = MaterialTheme.typography.labelSmall)
                            Text(Money.format(docCalc.grandTotal, currency), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (isSubmitting) return@OutlinedButton
                                    isSubmitting = true
                                    scope.launch {
                                        try {
                                            app.container.billingRepository.saveOrFinalizeInvoice(
                                                SaveInvoiceRequest(
                                                    existingInvoiceId = activeHeldInvoiceId,
                                                    documentType = InvoiceDocumentType.RETAIL_INVOICE,
                                                    holdAsPos = true,
                                                    customerId = selectedCustomer?.id,
                                                    customerName = selectedCustomer?.name ?: "Walk-in POS",
                                                    items = cart.toList(),
                                                    invoiceDiscountFlat = effectiveInvoiceDiscount
                                                )
                                            ).onSuccess {
                                                cart.clear()
                                                selectedCustomer = null
                                                discountText = "0"
                                                activeHeldInvoiceId = null
                                                snackbar.showSnackbar("Bill put on hold")
                                            }.onFailure { snackbar.showSnackbar(it.message ?: "Error") }
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                enabled = cart.isNotEmpty() && !isSubmitting
                            ) {
                                Icon(Icons.Default.PauseCircle, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hold")
                            }
                            Button(
                                onClick = {
                                    checkoutError = null
                                    showCheckoutDialog = true
                                },
                                enabled = cart.isNotEmpty() && !isSubmitting
                            ) {
                                Text("Charge")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SearchField(query = search, onQueryChange = { search = it }, placeholder = "Search items or scan barcode")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Customer: ${selectedCustomer?.name ?: "Walk-in Customer"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = { showCustomerPicker = true }) {
                    Text(if (selectedCustomer == null) "Attach Customer" else "Change")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }
            if (filteredProducts.isEmpty()) {
                EmptyStateCard(
                    title = "No POS Products",
                    description = "Add products to your catalog to start fast counter billing.",
                    actionLabel = "Add Product",
                    onAction = onAddProduct
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts, key = { it.id }) { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addToCart(p) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text(
                                    Money.formatPaise(p.sellingPricePaise, currency),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Stock: ${Money.formatQty(Money.fromPaise(p.currentStockScaled))} ${p.unitName}",
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

    if (showScanner) {
        BarcodeScannerSheet(
            onBarcodeScanned = { code ->
                showScanner = false
                scope.launch {
                    val found = app.container.inventoryRepository.findByBarcode(code)
                    if (found != null) {
                        addToCart(found)
                        snackbar.showSnackbar("Added ${found.name}")
                    } else {
                        snackbar.showSnackbar("Barcode $code not found")
                    }
                }
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showHeldDialog) {
        AlertDialog(
            onDismissRequest = { showHeldDialog = false },
            title = { Text("Resume Held POS Bill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    heldBills.forEach { held ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val bundle = app.container.billingRepository.getInvoiceWithItemsOnce(held.id)
                                        if (bundle != null) {
                                            cart.clear()
                                            cart.addAll(bundle.second.map {
                                                InvoiceLineDraft(
                                                    productId = it.productId,
                                                    productName = it.productName,
                                                    hsnSacCode = it.hsnSacCode,
                                                    unitName = it.unitName,
                                                    quantity = Money.fromPaise(it.quantityScaled),
                                                    rate = Money.fromPaise(it.ratePaise),
                                                    purchaseCost = Money.fromPaise(it.purchaseCostPaise),
                                                    taxRatePercent = Money.fromPaise(it.taxRatePercentScaled),
                                                    isTaxInclusive = it.isTaxInclusive
                                                )
                                            })
                                            activeHeldInvoiceId = held.id
                                            selectedCustomer = customers.firstOrNull { it.id == held.customerId }
                                            showHeldDialog = false
                                        }
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(held.customerName)
                                Text(Money.formatPaise(held.grandTotalPaise, currency), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHeldDialog = false }) { Text("Close") } }
        )
    }

    if (showCheckoutDialog) {
        val splitTotal = Money.of(splitCash).max(BigDecimal.ZERO) + Money.of(splitUpi).max(BigDecimal.ZERO)
        val splitBalance = (docCalc.grandTotal - splitTotal).max(BigDecimal.ZERO)
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showCheckoutDialog = false },
            title = { Text("POS Checkout • ${Money.format(docCalc.grandTotal, currency)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (checkoutError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                checkoutError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it; checkoutError = null },
                        label = { Text("Flat Discount ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.CARD).forEach { m ->
                            FilterChip(
                                selected = !useSplitPayment && paymentMode == m,
                                onClick = { useSplitPayment = false; paymentMode = m; checkoutError = null },
                                label = { Text(m.label) }
                            )
                        }
                        FilterChip(
                            selected = useSplitPayment,
                            onClick = { useSplitPayment = true; checkoutError = null },
                            label = { Text("Split") }
                        )
                    }
                    if (useSplitPayment) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = splitCash, onValueChange = { splitCash = it; checkoutError = null }, label = { Text("Cash") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(value = splitUpi, onValueChange = { splitUpi = it; checkoutError = null }, label = { Text("UPI") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        Text(
                            "Paid: ${Money.format(splitTotal.min(docCalc.grandTotal), currency)} • Balance Due: ${Money.format(splitBalance, currency)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text("Customer: ${selectedCustomer?.name ?: "Walk-in Customer"}", style = MaterialTheme.typography.bodyMedium)
                    if (loyaltyConfig?.isEnabled == true && selectedLoyaltyAccount != null && selectedLoyaltyAccount.pointsBalance > 0L) {
                        val cfg = loyaltyConfig!!
                        OutlinedTextField(
                            value = redeemPointsText,
                            onValueChange = { txt -> redeemPointsText = txt.filter(Char::isDigit) },
                            label = { Text("Redeem Loyalty Points (Avail: ${selectedLoyaltyAccount.pointsBalance})") },
                            supportingText = {
                                Text("Min ${cfg.minRedeemPoints}, Max ${cfg.maxRedeemPointsPerInvoice} pts • Off: ${Money.format(loyaltyDiscountBd, currency)}")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { finalizePosCheckout() },
                    enabled = !isSubmitting
                ) { Text(if (isSubmitting) "Billing..." else "Complete & Bill") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCheckoutDialog = false },
                    enabled = !isSubmitting
                ) { Text("Cancel") }
            }
        )
    }

    if (editingCartIndex != null) {
        val idx = editingCartIndex!!
        val line = cart.getOrNull(idx)
        if (line != null) {
            var qtyTxt by remember(idx) { mutableStateOf(Money.formatPlain(line.quantity)) }
            var rateTxt by remember(idx) { mutableStateOf(Money.formatPlain(line.rate)) }
            var discTxt by remember(idx) { mutableStateOf(Money.formatPlain(line.discountPercent)) }
            AlertDialog(
                onDismissRequest = { editingCartIndex = null },
                title = { Text("Edit ${line.productName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = qtyTxt,
                            onValueChange = { qtyTxt = it },
                            label = { Text("Quantity (${line.unitName})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rateTxt,
                            onValueChange = { rateTxt = it },
                            label = { Text("Rate ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = discTxt,
                            onValueChange = { discTxt = it },
                            label = { Text("Discount %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val newQty = Money.of(qtyTxt).max(BigDecimal("0.01"))
                        val newRate = Money.of(rateTxt).max(BigDecimal.ZERO)
                        val newDisc = Money.of(discTxt).max(BigDecimal.ZERO).min(BigDecimal("100"))
                        val matchedProd = products.firstOrNull { it.id == line.productId }
                        val allowNeg = appSettings?.allowNegativeStock == true
                        if (matchedProd != null && !allowNeg && !matchedProd.isServiceItem) {
                            val avail = Money.fromPaise(matchedProd.currentStockScaled)
                            if (newQty > avail) {
                                scope.launch { snackbar.showSnackbar("Only ${Money.formatQty(avail)} ${matchedProd.unitName} available") }
                                return@Button
                            }
                        }
                        cart[idx] = line.copy(quantity = newQty, rate = newRate, discountPercent = newDisc)
                        editingCartIndex = null
                    }) { Text("Apply") }
                },
                dismissButton = { OutlinedButton(onClick = { editingCartIndex = null }) { Text("Cancel") } }
            )
        }
    }

    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("Select POS Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selectedCustomer = null
                        redeemPointsText = ""
                        showCustomerPicker = false
                    }) { Text("Walk-in Customer") }
                    customers.take(25).forEach { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedCustomer = c
                                showCustomerPicker = false
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.name, fontWeight = FontWeight.Medium)
                                if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCustomerPicker = false }) { Text("Close") } }
        )
    }

    if (expiredBatchWarning != null) {
        AlertDialog(
            onDismissRequest = { expiredBatchWarning = null },
            title = { Text("Expired Batch Warning") },
            text = { Text(expiredBatchWarning!!) },
            confirmButton = {
                Button(onClick = {
                    expiredBatchWarning = null
                    finalizePosCheckout(allowExpiredOverride = true)
                }) { Text("Override & Bill") }
            },
            dismissButton = {
                OutlinedButton(onClick = { expiredBatchWarning = null }) { Text("Cancel") }
            }
        )
    }
}
