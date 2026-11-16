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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.core.GstCalculator
import com.example.core.GstSupplyType
import com.example.core.LineCalculationInput
import com.example.core.Money
import com.example.data.local.entities.Customer
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Product
import com.example.data.local.entities.ProductBatch
import com.example.data.local.entities.SearchModule
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.SaveInvoiceRequest
import com.example.ui.components.BarcodeScannerSheet
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.RecentSearchHistoryBar
import com.example.ui.components.SearchField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditInvoiceScreen(
    existingInvoiceId: Long?,
    initialDocumentType: String,
    presetCustomerId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val customers by app.container.partyRepository.customers.collectAsStateWithLifecycle(emptyList())
    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val loyaltyConfig by app.container.engagementRepository.loyaltyConfig.collectAsStateWithLifecycle(null)
    val loyaltyAccounts by app.container.engagementRepository.loyaltyAccounts.collectAsStateWithLifecycle(emptyList())
    val currency = profile?.currencySymbol ?: "₹"

    var docType by remember {
        mutableStateOf(runCatching { InvoiceDocumentType.valueOf(initialDocumentType) }.getOrDefault(InvoiceDocumentType.TAX_INVOICE))
    }
    var invoiceNumber by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerName by remember { mutableStateOf("Walk-in Customer") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var customerGstin by remember { mutableStateOf("") }
    var isInterState by remember { mutableStateOf(false) }

    val items = remember { mutableStateListOf<InvoiceLineDraft>() }

    var invoiceDiscountFlat by remember { mutableStateOf("0") }
    var additionalCharges by remember { mutableStateOf("0") }
    var shippingCharges by remember { mutableStateOf("0") }
    var enableRoundOff by remember { mutableStateOf(true) }
    var paidAmountText by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("") }
    var tableNumber by remember { mutableStateOf("") }

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var redeemPointsText by remember { mutableStateOf("") }
    var expiredBatchPrompt by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var batchPickerLineIdx by remember { mutableStateOf<Int?>(null) }
    var batchPickerOptions by remember { mutableStateOf<List<ProductBatch>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(existingInvoiceId, presetCustomerId, customers) {
        if (!initialized) {
            if (existingInvoiceId != null) {
                val bundle = app.container.billingRepository.getInvoiceWithItemsOnce(existingInvoiceId)
                if (bundle != null) {
                    val (inv, invItems) = bundle
                    docType = inv.documentType
                    invoiceNumber = inv.invoiceNumber
                    customerName = inv.customerName
                    customerPhone = inv.customerPhone
                    customerAddress = inv.customerAddress
                    customerGstin = inv.customerGstin
                    isInterState = inv.isInterState
                    selectedCustomer = customers.firstOrNull { it.id == inv.customerId }
                    invoiceDiscountFlat = Money.formatPlain(Money.fromPaise(inv.invoiceDiscountFlatPaise))
                    additionalCharges = Money.formatPlain(Money.fromPaise(inv.additionalChargesPaise))
                    shippingCharges = Money.formatPlain(Money.fromPaise(inv.shippingChargesPaise))
                    paidAmountText = if (inv.paidAmountPaise > 0L) Money.formatPlain(Money.fromPaise(inv.paidAmountPaise)) else ""
                    paymentMode = inv.paymentMode
                    notes = inv.notes
                    terms = inv.termsAndConditions
                    tableNumber = inv.tableNumber
                    items.clear()
                    items.addAll(invItems.map {
                        InvoiceLineDraft(
                            productId = it.productId,
                            batchId = it.batchId,
                            productName = it.productName,
                            productCode = it.productCode,
                            hsnSacCode = it.hsnSacCode,
                            unitName = it.unitName,
                            quantity = Money.fromPaise(it.quantityScaled),
                            rate = Money.fromPaise(it.ratePaise),
                            purchaseCost = Money.fromPaise(it.purchaseCostPaise),
                            discountPercent = Money.fromPaise(it.discountPercentScaled),
                            taxRatePercent = Money.fromPaise(it.taxRatePercentScaled),
                            isTaxInclusive = it.isTaxInclusive,
                            batchNumber = it.batchNumber,
                            expiryDateMillis = it.expiryDateMillis,
                            serialNumber = it.serialNumber
                        )
                    })
                    if (inv.loyaltyPointsRedeemed > 0L) {
                        redeemPointsText = inv.loyaltyPointsRedeemed.toString()
                    }
                    initialized = true
                }
            } else {
                invoiceNumber = app.container.billingRepository.generateNextInvoiceNumber()
                terms = profile?.defaultTerms ?: "Thank you for your business!"
                enableRoundOff = settings?.enableRoundOff ?: true
                if (presetCustomerId != null) {
                    val c = customers.firstOrNull { it.id == presetCustomerId }
                    if (c != null) {
                        selectedCustomer = c
                        customerName = c.name
                        customerPhone = c.phone
                        customerAddress = c.address
                        customerGstin = c.gstin
                    }
                }
                if (customers.isNotEmpty() || presetCustomerId == null) {
                    initialized = true
                }
            }
        }
    }

    val supplyType = when {
        docType == InvoiceDocumentType.NON_GST_INVOICE -> GstSupplyType.NON_GST
        isInterState -> GstSupplyType.INTER_STATE
        else -> GstSupplyType.INTRA_STATE
    }

    val selectedLoyaltyAccount = selectedCustomer?.let { c ->
        loyaltyAccounts.firstOrNull { it.customerId == c.id }
    }
    val requestedRedeemPoints = redeemPointsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val validRedeemPoints = if (
        loyaltyConfig?.isEnabled == true &&
        selectedLoyaltyAccount != null &&
        requestedRedeemPoints >= (loyaltyConfig?.minRedeemPoints ?: 0L)
    ) {
        requestedRedeemPoints
            .coerceAtMost(selectedLoyaltyAccount.pointsBalance)
            .coerceAtMost(loyaltyConfig?.maxRedeemPointsPerInvoice ?: 0L)
    } else 0L
    val loyaltyDiscountBd = Money.fromPaise(validRedeemPoints * (loyaltyConfig?.pointValuePaise ?: 0L))

    val effectiveInvoiceDiscount = Money.of(invoiceDiscountFlat).max(BigDecimal.ZERO)
    val effectiveAdditionalCharges = Money.of(additionalCharges).max(BigDecimal.ZERO)
    val effectiveShippingCharges = Money.of(shippingCharges).max(BigDecimal.ZERO)

    val calc = GstCalculator.calculateDocument(
        lines = items.map {
            LineCalculationInput(
                quantity = it.quantity,
                unitPrice = it.rate,
                discountPercent = it.discountPercent,
                discountAmount = it.discountFlat,
                taxRatePercent = it.taxRatePercent,
                isTaxInclusive = it.isTaxInclusive
            )
        },
        supplyType = supplyType,
        invoiceDiscountFlat = effectiveInvoiceDiscount + loyaltyDiscountBd,
        additionalCharges = effectiveAdditionalCharges,
        shippingCharges = effectiveShippingCharges,
        enableRoundOff = enableRoundOff
    )

    val paidBd = Money.of(paidAmountText).max(BigDecimal.ZERO).min(calc.grandTotal)
    val balanceDueBd = (calc.grandTotal - paidBd).max(BigDecimal.ZERO)

    fun addProductToInvoice(product: Product) {
        scope.launch {
            val allowNeg = settings?.allowNegativeStock == true
            val available = Money.fromPaise(product.currentStockScaled)
            val existingIdx = items.indexOfFirst { it.productId == product.id }
            if (existingIdx >= 0) {
                val cur = items[existingIdx]
                val nextQty = cur.quantity + BigDecimal.ONE
                if (docType.affectsStock && docType != InvoiceDocumentType.SALES_RETURN &&
                    !allowNeg && !product.isServiceItem && nextQty > available
                ) {
                    snackbar.showSnackbar("Warning: ${product.name} qty exceeds available stock (${Money.formatQty(available)} ${product.unitName})")
                }
                val tierWholesale = product.wholesalePricePaise > 0L && (
                    docType == InvoiceDocumentType.WHOLESALE_INVOICE ||
                        (settings?.enableWholesaleModule == true && nextQty >= Money.fromPaise(product.wholesaleMinQtyScaled).max(BigDecimal.ONE))
                    )
                items[existingIdx] = cur.copy(
                    quantity = nextQty,
                    rate = if (tierWholesale) Money.fromPaise(product.wholesalePricePaise) else cur.rate
                )
            } else {
                if (docType.affectsStock && docType != InvoiceDocumentType.SALES_RETURN &&
                    !allowNeg && !product.isServiceItem && available < BigDecimal.ONE
                ) {
                    snackbar.showSnackbar("Warning: ${product.name} is currently out of stock")
                }
                val batches = app.container.intelligenceRepository
                    .observeBatchesForProduct(product.id)
                    .firstOrNull()
                    .orEmpty()
                val now = System.currentTimeMillis()
                val preferredBatch = batches.firstOrNull { it.expiryDateMillis == null || it.expiryDateMillis >= now }
                    ?: batches.firstOrNull()
                val useWholesale = docType == InvoiceDocumentType.WHOLESALE_INVOICE && product.wholesalePricePaise > 0L
                items.add(
                    InvoiceLineDraft(
                        productId = product.id,
                        batchId = preferredBatch?.id,
                        productName = product.name,
                        productCode = product.productCode.ifBlank { product.sku },
                        hsnSacCode = product.hsnSacCode,
                        unitName = product.unitName,
                        quantity = BigDecimal.ONE,
                        rate = Money.fromPaise(if (useWholesale) product.wholesalePricePaise else product.sellingPricePaise),
                        purchaseCost = Money.fromPaise(preferredBatch?.unitCostPaise ?: product.purchasePricePaise),
                        taxRatePercent = Money.fromPaise(product.taxRatePercentScaled),
                        isTaxInclusive = product.isTaxInclusive,
                        batchNumber = preferredBatch?.batchNumber ?: product.batchNumber,
                        expiryDateMillis = preferredBatch?.expiryDateMillis ?: product.expiryDateMillis,
                        serialNumber = product.serialNumber
                    )
                )
            }
        }
    }

    fun submitInvoice(saveAsDraft: Boolean, allowExpiredOverride: Boolean = false) {
        if (isSubmitting) return
        isSubmitting = true
        scope.launch {
            try {
                val result = app.container.billingRepository.saveOrFinalizeInvoice(
                    SaveInvoiceRequest(
                        existingInvoiceId = existingInvoiceId,
                        invoiceNumber = invoiceNumber.ifBlank { null },
                        documentType = docType,
                        saveAsDraft = saveAsDraft,
                        invoiceDateMillis = System.currentTimeMillis(),
                        customerId = selectedCustomer?.id,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        customerAddress = customerAddress,
                        customerGstin = customerGstin,
                        isInterState = isInterState,
                        items = items.toList(),
                        invoiceDiscountFlat = effectiveInvoiceDiscount,
                        additionalCharges = effectiveAdditionalCharges,
                        shippingCharges = effectiveShippingCharges,
                        enableRoundOff = enableRoundOff,
                        paidAmount = paidBd,
                        paymentMode = paymentMode,
                        redeemLoyaltyPoints = validRedeemPoints,
                        allowExpiredBatchOverride = allowExpiredOverride,
                        tableNumber = tableNumber,
                        notes = notes,
                        termsAndConditions = terms
                    )
                )
                result.onSuccess { savedId ->
                    onSaved(savedId)
                }.onFailure { err ->
                    val msg = err.message ?: "Unable to save invoice"
                    if (msg.startsWith("EXPIRED_BATCH_WARNING:")) {
                        expiredBatchPrompt = msg.removePrefix("EXPIRED_BATCH_WARNING:").trim() to saveAsDraft
                    } else {
                        snackbar.showSnackbar(msg)
                    }
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = if (existingInvoiceId != null) "Edit Draft Bill" else "New ${docType.label}",
                subtitle = invoiceNumber,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showBarcodeScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
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
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InvoiceDocumentType.entries.forEach { type ->
                        FilterChip(
                            selected = docType == type,
                            onClick = { docType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = invoiceNumber,
                            onValueChange = { invoiceNumber = it },
                            label = { Text("Document Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Party / Customer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(customerName, style = MaterialTheme.typography.titleMedium)
                                if (customerPhone.isNotBlank()) Text(customerPhone, style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedButton(onClick = { showCustomerPicker = true }, modifier = Modifier.testTag("select_customer_btn")) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text("Select")
                            }
                        }
                        if (docType != InvoiceDocumentType.NON_GST_INVOICE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Inter-State Supply (IGST instead of CGST+SGST)", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = isInterState, onCheckedChange = { isInterState = it })
                            }
                        }
                        if (settings?.enableRestaurantModule == true) {
                            OutlinedTextField(
                                value = tableNumber,
                                onValueChange = { tableNumber = it },
                                label = { Text("Table / KOT Reference") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Line Items (${items.size})", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCustomItemDialog = true }) { Text("Custom") }
                        Button(onClick = { showProductPicker = true }, modifier = Modifier.testTag("add_item_btn")) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add Item")
                        }
                    }
                }
            }

            itemsIndexed(items) { index, line ->
                val lineRes = GstCalculator.calculateLine(
                    LineCalculationInput(line.quantity, line.rate, line.discountPercent, line.discountFlat, line.taxRatePercent, line.isTaxInclusive),
                    supplyType
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingLineIndex = index },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Money.formatQty(line.quantity)} ${line.unitName} x ${Money.format(line.rate, currency)}  • Tax ${Money.formatQty(line.taxRatePercent)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val linkedProduct = line.productId?.let { pid -> products.firstOrNull { it.id == pid } }
                            if (linkedProduct != null && docType.affectsStock && docType != InvoiceDocumentType.SALES_RETURN &&
                                settings?.allowNegativeStock == false && !linkedProduct.isServiceItem
                            ) {
                                val avail = Money.fromPaise(linkedProduct.currentStockScaled)
                                if (line.quantity > avail) {
                                    Text(
                                        "Insufficient stock (Avail: ${Money.formatQty(avail)} ${linkedProduct.unitName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (line.batchNumber.isNotBlank() || line.expiryDateMillis != null) {
                                val expLabel = line.expiryDateMillis?.let { " • Exp ${DateUtils.formatDate(it)}" } ?: ""
                                val isExp = line.expiryDateMillis != null && line.expiryDateMillis < System.currentTimeMillis()
                                Text(
                                    "Batch: ${line.batchNumber.ifBlank { "Assigned" }}$expLabel${if (isExp) " (EXPIRED)" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isExp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tap to edit qty, rate, discount or tax", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                if (line.productId != null) {
                                    Text(
                                        "Change Batch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                val b = app.container.intelligenceRepository
                                                    .observeBatchesForProduct(line.productId)
                                                    .firstOrNull()
                                                    .orEmpty()
                                                if (b.isEmpty()) {
                                                    snackbar.showSnackbar("No active batches for ${line.productName}")
                                                } else {
                                                    batchPickerOptions = b
                                                    batchPickerLineIdx = index
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Money.format(lineRes.lineTotal, currency), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { items.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Totals & Charges", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = invoiceDiscountFlat,
                                onValueChange = { invoiceDiscountFlat = it },
                                label = { Text("Discount ($currency)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = additionalCharges,
                                onValueChange = { additionalCharges = it },
                                label = { Text("Extra Charges") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = shippingCharges,
                                onValueChange = { shippingCharges = it },
                                label = { Text("Shipping") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto Round-Off")
                            Switch(checked = enableRoundOff, onCheckedChange = { enableRoundOff = it })
                        }
                        if (loyaltyConfig?.isEnabled == true && selectedLoyaltyAccount != null && selectedLoyaltyAccount.pointsBalance > 0L) {
                            val cfg = loyaltyConfig!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = redeemPointsText,
                                    onValueChange = { txt -> redeemPointsText = txt.filter(Char::isDigit) },
                                    label = { Text("Redeem Points (Avail: ${selectedLoyaltyAccount.pointsBalance})") },
                                    supportingText = {
                                        Text("Min ${cfg.minRedeemPoints}, Max ${cfg.maxRedeemPointsPerInvoice} pts • Discount: ${Money.format(loyaltyDiscountBd, currency)}")
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    val maxPts = selectedLoyaltyAccount.pointsBalance.coerceAtMost(cfg.maxRedeemPointsPerInvoice)
                                    redeemPointsText = maxPts.toString()
                                }) {
                                    Text("Max")
                                }
                            }
                        }
                        SummaryRow("Subtotal", Money.format(calc.subtotal, currency))
                        SummaryRow("Taxable Amount", Money.format(calc.taxableAmount, currency))
                        if (calc.cgstTotal > BigDecimal.ZERO) SummaryRow("CGST", Money.format(calc.cgstTotal, currency))
                        if (calc.sgstTotal > BigDecimal.ZERO) SummaryRow("SGST", Money.format(calc.sgstTotal, currency))
                        if (calc.igstTotal > BigDecimal.ZERO) SummaryRow("IGST", Money.format(calc.igstTotal, currency))
                        if (loyaltyDiscountBd > BigDecimal.ZERO) SummaryRow("Loyalty Redemption", "-${Money.format(loyaltyDiscountBd, currency)}")
                        if (calc.roundOff.compareTo(BigDecimal.ZERO) != 0) SummaryRow("Round-Off", Money.format(calc.roundOff, currency))
                        SummaryRow("Grand Total", Money.format(calc.grandTotal, currency), bold = true)
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Payment & Notes", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaymentMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = paymentMode == mode,
                                    onClick = { paymentMode = mode },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = paidAmountText,
                                onValueChange = { paidAmountText = it },
                                label = { Text("Amount Paid ($currency)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("invoice_paid_amount")
                            )
                            TextButton(onClick = { paidAmountText = Money.formatPlain(calc.grandTotal) }) {
                                Text("Full Paid")
                            }
                        }
                        SummaryRow("Balance Due", Money.format(balanceDueBd, currency), bold = true)
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = terms,
                            onValueChange = { terms = it },
                            label = { Text("Terms & Conditions") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { submitInvoice(saveAsDraft = true) },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f).height(52.dp).testTag("save_draft_btn")
                    ) { Text(if (isSubmitting) "Saving..." else "Save Draft") }
                    Button(
                        onClick = { submitInvoice(saveAsDraft = false) },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1.3f).height(52.dp).testTag("finalize_invoice_btn")
                    ) { Text(if (isSubmitting) "Finalizing..." else "Finalize Bill") }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showCustomerPicker) {
        ModalBottomSheet(onDismissRequest = { showCustomerPicker = false }) {
            val recentInvoiceSearches by app.container.searchHistoryRepository
                .observeRecentSearches(SearchModule.INVOICE)
                .collectAsStateWithLifecycle(emptyList())
            var q by remember { mutableStateOf("") }
            LaunchedEffect(q) {
                val trimmed = q.trim()
                if (trimmed.length >= 2) {
                    delay(900L)
                    app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Customer", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SearchField(
                    query = q,
                    onQueryChange = { q = it },
                    placeholder = "Search customer",
                    onSearchSubmit = {
                        val trimmed = q.trim()
                        if (trimmed.length >= 2) {
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                            }
                        }
                    }
                )
                if (recentInvoiceSearches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    RecentSearchHistoryBar(
                        history = recentInvoiceSearches,
                        onSelectQuery = { selected ->
                            q = selected
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
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    selectedCustomer = null
                    customerName = "Walk-in Customer"
                    customerPhone = ""
                    customerAddress = ""
                    customerGstin = ""
                    showCustomerPicker = false
                }) { Text("Use Walk-in Customer") }
                val list = remember(customers, q) {
                    customers.filter { q.isBlank() || it.name.contains(q, true) || it.phone.contains(q, true) }
                }
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    itemsIndexed(list, key = { _, c -> c.id }) { _, c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val trimmed = q.trim()
                                    if (trimmed.length >= 2) {
                                        scope.launch {
                                            app.container.searchHistoryRepository.recordSearch(SearchModule.INVOICE, trimmed)
                                        }
                                    }
                                    selectedCustomer = c
                                    customerName = c.name
                                    customerPhone = c.phone
                                    customerAddress = c.address
                                    customerGstin = c.gstin
                                    showCustomerPicker = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(Money.formatPaise(c.currentBalancePaise, currency))
                        }
                    }
                }
            }
        }
    }

    if (showProductPicker) {
        ModalBottomSheet(onDismissRequest = { showProductPicker = false }) {
            val recentInventorySearches by app.container.searchHistoryRepository
                .observeRecentSearches(SearchModule.INVENTORY)
                .collectAsStateWithLifecycle(emptyList())
            var q by remember { mutableStateOf("") }
            LaunchedEffect(q) {
                val trimmed = q.trim()
                if (trimmed.length >= 2) {
                    delay(900L)
                    app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Product", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SearchField(
                    query = q,
                    onQueryChange = { q = it },
                    placeholder = "Search product name, SKU or barcode",
                    onSearchSubmit = {
                        val trimmed = q.trim()
                        if (trimmed.length >= 2) {
                            scope.launch {
                                app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
                            }
                        }
                    }
                )
                if (recentInventorySearches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    RecentSearchHistoryBar(
                        history = recentInventorySearches,
                        onSelectQuery = { selected ->
                            q = selected
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
                Spacer(modifier = Modifier.height(8.dp))
                val list = remember(products, q) {
                    products.filter {
                        q.isBlank() || it.name.contains(q, true) || it.barcode.contains(q, true) || it.sku.contains(q, true)
                    }
                }
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    itemsIndexed(list, key = { _, p -> p.id }) { _, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val trimmed = q.trim()
                                    if (trimmed.length >= 2) {
                                        scope.launch {
                                            app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
                                        }
                                    }
                                    addProductToInvoice(p)
                                    showProductPicker = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("Stock: ${Money.formatQty(Money.fromPaise(p.currentStockScaled))} ${p.unitName} • Tax ${Money.formatQty(Money.fromPaise(p.taxRatePercentScaled))}%", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(Money.formatPaise(p.sellingPricePaise, currency), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showBarcodeScanner) {
        BarcodeScannerSheet(
            onBarcodeScanned = { code ->
                showBarcodeScanner = false
                scope.launch {
                    val match = app.container.inventoryRepository.findByBarcode(code)
                    if (match != null) {
                        addProductToInvoice(match)
                        snackbar.showSnackbar("Added ${match.name}")
                    } else {
                        snackbar.showSnackbar("No product matched barcode $code")
                    }
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    if (editingLineIndex != null) {
        val idx = editingLineIndex!!
        val line = items[idx]
        LineEditDialog(
            initial = line,
            onDismiss = { editingLineIndex = null },
            onSave = { updated ->
                items[idx] = updated
                editingLineIndex = null
            }
        )
    }

    if (showCustomItemDialog) {
        LineEditDialog(
            initial = InvoiceLineDraft(productName = "", quantity = BigDecimal.ONE, rate = BigDecimal.ZERO),
            onDismiss = { showCustomItemDialog = false },
            onSave = { added ->
                if (added.productName.isNotBlank()) items.add(added)
                showCustomItemDialog = false
            }
        )
    }

    if (expiredBatchPrompt != null) {
        val (warningMsg, isDraft) = expiredBatchPrompt!!
        AlertDialog(
            onDismissRequest = { expiredBatchPrompt = null },
            title = { Text("Expired Batch Warning") },
            text = { Text(warningMsg) },
            confirmButton = {
                Button(onClick = {
                    expiredBatchPrompt = null
                    submitInvoice(saveAsDraft = isDraft, allowExpiredOverride = true)
                }) { Text("Override & Proceed") }
            },
            dismissButton = {
                OutlinedButton(onClick = { expiredBatchPrompt = null }) { Text("Cancel") }
            }
        )
    }

    if (batchPickerLineIdx != null) {
        val idx = batchPickerLineIdx!!
        AlertDialog(
            onDismissRequest = { batchPickerLineIdx = null },
            title = { Text("Select Batch (FEFO)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    batchPickerOptions.forEach { batch ->
                        val isExp = batch.expiryDateMillis != null && batch.expiryDateMillis < System.currentTimeMillis()
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val cur = items[idx]
                                items[idx] = cur.copy(
                                    batchId = batch.id,
                                    batchNumber = batch.batchNumber,
                                    expiryDateMillis = batch.expiryDateMillis,
                                    purchaseCost = Money.fromPaise(batch.unitCostPaise)
                                )
                                batchPickerLineIdx = null
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "${batch.batchNumber}${if (isExp) " (EXPIRED)" else ""}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Rem: ${Money.formatQty(Money.fromPaise(batch.remainingQuantityScaled))} • Exp: ${batch.expiryDateMillis?.let { DateUtils.formatDate(it) } ?: "None"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { batchPickerLineIdx = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun LineEditDialog(
    initial: InvoiceLineDraft,
    onDismiss: () -> Unit,
    onSave: (InvoiceLineDraft) -> Unit
) {
    var name by remember { mutableStateOf(initial.productName) }
    var qty by remember { mutableStateOf(Money.formatPlain(initial.quantity)) }
    var rate by remember { mutableStateOf(Money.formatPlain(initial.rate)) }
    var disc by remember { mutableStateOf(Money.formatPlain(initial.discountPercent)) }
    var tax by remember { mutableStateOf(Money.formatPlain(initial.taxRatePercent)) }
    var inclusive by remember { mutableStateOf(initial.isTaxInclusive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.productName.isBlank()) "Add Custom Item" else "Edit Line Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = disc, onValueChange = { disc = it }, label = { Text("Disc %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("GST %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tax Inclusive Rate")
                    Switch(checked = inclusive, onCheckedChange = { inclusive = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    initial.copy(
                        productName = name.trim().ifBlank { "Item" },
                        quantity = Money.of(qty).max(BigDecimal("0.01")),
                        rate = Money.of(rate).max(BigDecimal.ZERO),
                        discountPercent = Money.of(disc).max(BigDecimal.ZERO).min(BigDecimal("100")),
                        taxRatePercent = Money.of(tax).max(BigDecimal.ZERO).min(BigDecimal("100")),
                        isTaxInclusive = inclusive
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
