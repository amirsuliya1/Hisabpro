package com.example.ui.screens.items

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.HisabPulseApp
import com.example.core.BarcodeUtils
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.entities.Product
import com.example.data.local.entities.SearchModule
import com.example.data.local.entities.UnitEntity
import com.example.ui.components.BarcodeScannerSheet
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.RecentSearchHistoryBar
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onAddProduct: () -> Unit,
    onOpenProductDetail: (Long) -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenUnits: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val products by app.container.inventoryRepository.activeProducts.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false
    val semantic = LocalSemanticColors.current

    val recentSearches by app.container.searchHistoryRepository
        .observeRecentSearches(SearchModule.INVENTORY)
        .collectAsStateWithLifecycle(emptyList())

    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showScanner by remember { mutableStateOf(false) }
    var showBulkPriceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(search) {
        val trimmed = search.trim()
        if (trimmed.length >= 2) {
            delay(900L)
            app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines().orEmpty()
                    var imported = 0
                    lines.drop(1).forEach { line ->
                        val cols = line.split(",").map { it.trim().trim('"') }
                        if (cols.isNotEmpty() && cols[0].isNotBlank()) {
                            val name = cols[0]
                            val selling = Money.safeToPaise(Money.of(cols.getOrNull(1)))
                            val purchase = Money.safeToPaise(Money.of(cols.getOrNull(2)))
                            val stock = Money.safeToPaise(Money.of(cols.getOrNull(3)))
                            val tax = Money.safeToPaise(Money.of(cols.getOrNull(4)))
                            app.container.inventoryRepository.saveProduct(
                                Product(
                                    name = name,
                                    sellingPricePaise = selling,
                                    purchasePricePaise = purchase,
                                    openingStockScaled = stock,
                                    taxRatePercentScaled = tax
                                )
                            )
                            imported++
                        }
                    }
                    snackbar.showSnackbar("Imported $imported products from CSV")
                }.onFailure { snackbar.showSnackbar(it.message ?: "CSV import failed") }
            }
        }
    }

    val categories = remember(products) { listOf("All") + products.map { it.categoryName }.distinct().sorted() }
    val filtered = remember(products, selectedCategory, search) {
        products.filter { p ->
            val catOk = selectedCategory == "All" || p.categoryName == selectedCategory
            val qOk = search.isBlank() || p.name.contains(search, true) || p.barcode.contains(search, true) || p.sku.contains(search, true)
            catOk && qOk
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Items & Inventory") },
                actions = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                    IconButton(onClick = onOpenInventory) {
                        Icon(Icons.Default.Inventory, contentDescription = "Inventory management")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProduct, modifier = Modifier.testTag("fab_add_product")) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = onOpenInventory, label = { Text("Inventory") }, leadingIcon = { Icon(Icons.Default.Inventory, null) })
                    AssistChip(onClick = onOpenCategories, label = { Text("Categories") }, leadingIcon = { Icon(Icons.Default.Category, null) })
                    AssistChip(onClick = onOpenUnits, label = { Text("Units") }, leadingIcon = { Icon(Icons.Default.Straighten, null) })
                    AssistChip(onClick = { showBulkPriceDialog = true }, label = { Text("Bulk Price") }, leadingIcon = { Icon(Icons.Default.PriceChange, null) })
                    AssistChip(onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }, label = { Text("Import CSV") }, leadingIcon = { Icon(Icons.Default.UploadFile, null) })
                }
            }
            item {
                SearchField(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "Search items, SKU or barcode",
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(c) })
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Products Yet",
                        description = "Add products with pricing, GST rate, and opening stock to start billing and tracking inventory.",
                        actionLabel = "Add First Product",
                        onAction = onAddProduct
                    )
                }
            } else {
                items(filtered, key = { it.id }) { p ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val trimmed = search.trim()
                            if (trimmed.length >= 2) {
                                scope.launch {
                                    app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, trimmed)
                                }
                            }
                            onOpenProductDetail(p.id)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${p.categoryName} • Tax ${Money.formatQty(Money.fromPaise(p.taxRatePercentScaled))}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!p.isServiceItem) {
                                    val isOut = p.currentStockScaled <= 0L
                                    val isLow = !isOut && p.currentStockScaled <= p.minStockScaled
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusPill(
                                        label = "Stock: ${Money.formatQty(Money.fromPaise(p.currentStockScaled))} ${p.unitName}",
                                        containerColor = when {
                                            isOut -> semantic.dangerContainer
                                            isLow -> semantic.warningContainer
                                            else -> semantic.successContainer
                                        },
                                        contentColor = when {
                                            isOut -> semantic.danger
                                            isLow -> semantic.warning
                                            else -> semantic.success
                                        }
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(p.sellingPricePaise, currency, hide)
                                Text("MRP ${Money.formatPaise(p.mrpPaise, currency, hide)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                search = code
                scope.launch {
                    app.container.searchHistoryRepository.recordSearch(SearchModule.INVENTORY, code)
                }
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showBulkPriceDialog) {
        var pct by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { showBulkPriceDialog = false },
            title = { Text("Bulk Selling Price Update") },
            text = {
                OutlinedTextField(
                    value = pct,
                    onValueChange = { pct = it },
                    label = { Text("Percentage Change (e.g. 5 or -5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.inventoryRepository.bulkUpdateSellingPrices(
                            filtered.map { it.id },
                            Money.of(pct)
                        )
                        showBulkPriceDialog = false
                        snackbar.showSnackbar("Updated prices for ${filtered.size} products")
                    }
                }) { Text("Apply") }
            },
            dismissButton = { OutlinedButton(onClick = { showBulkPriceDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AddEditProductScreen(
    productId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val categories by app.container.businessRepository.categories.collectAsStateWithLifecycle(emptyList())
    val units by app.container.businessRepository.units.collectAsStateWithLifecycle(emptyList())
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)

    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var hsn by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("General") }
    var brand by remember { mutableStateOf("") }
    var unitName by remember { mutableStateOf("PCS") }
    var purchasePrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var wholesalePrice by remember { mutableStateOf("") }
    var mrp by remember { mutableStateOf("") }
    var taxPercent by remember { mutableStateOf("18") }
    var isTaxInclusive by remember { mutableStateOf(false) }
    var openingStock by remember { mutableStateOf("0") }
    var minStock by remember { mutableStateOf("5") }
    var batchNumber by remember { mutableStateOf("") }
    var garmentSize by remember { mutableStateOf("") }
    var garmentColor by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var warrantyMonths by remember { mutableStateOf("") }
    var isServiceItem by remember { mutableStateOf(false) }
    var isRawMaterial by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    LaunchedEffect(productId) {
        if (productId != null && !loaded) {
            app.container.inventoryRepository.observeProduct(productId).collect { p ->
                if (p != null && !loaded) {
                    name = p.name
                    imageUri = p.imageUri
                    sku = p.sku
                    barcode = p.barcode
                    hsn = p.hsnSacCode
                    categoryName = p.categoryName
                    brand = p.brand
                    unitName = p.unitName
                    purchasePrice = Money.formatPlain(Money.fromPaise(p.purchasePricePaise))
                    sellingPrice = Money.formatPlain(Money.fromPaise(p.sellingPricePaise))
                    wholesalePrice = Money.formatPlain(Money.fromPaise(p.wholesalePricePaise))
                    mrp = Money.formatPlain(Money.fromPaise(p.mrpPaise))
                    taxPercent = Money.formatPlain(Money.fromPaise(p.taxRatePercentScaled))
                    isTaxInclusive = p.isTaxInclusive
                    openingStock = Money.formatPlain(Money.fromPaise(p.openingStockScaled))
                    minStock = Money.formatPlain(Money.fromPaise(p.minStockScaled))
                    batchNumber = p.batchNumber
                    garmentSize = p.garmentSize
                    garmentColor = p.garmentColor
                    serialNumber = p.serialNumber
                    warrantyMonths = if (p.warrantyMonths > 0) p.warrantyMonths.toString() else ""
                    isServiceItem = p.isServiceItem
                    isRawMaterial = p.isRawMaterial
                    description = p.description
                    loaded = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = if (productId == null) "Add Product" else "Edit Product",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUri.isNotBlank()) {
                            AsyncImage(model = imageUri, contentDescription = "Product image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Select product photo")
                        }
                    }
                    OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Pick Product Image")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product / Service Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU / Code") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = hsn, onValueChange = { hsn = it }, label = { Text("HSN / SAC") }, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan") }
                    IconButton(onClick = { barcode = "HP${System.currentTimeMillis().toString().takeLast(8)}" }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Generate barcode")
                    }
                }
            }
            item {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val catList = (categories.map { it.name } + "General").distinct()
                    catList.forEach { c ->
                        FilterChip(selected = categoryName == c, onClick = { categoryName = c }, label = { Text(c) })
                    }
                }
            }
            item {
                Text("Unit", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val unitList = (units.map { it.shortName } + "PCS").distinct()
                    unitList.forEach { u ->
                        FilterChip(selected = unitName == u, onClick = { unitName = u }, label = { Text(u) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text("Selling Price *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_selling_price")
                    )
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("Purchase Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_purchase_price")
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mrp,
                        onValueChange = { mrp = it },
                        label = { Text("MRP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = taxPercent,
                        onValueChange = { taxPercent = it },
                        label = { Text("GST %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Selling Price is Tax Inclusive")
                    Switch(checked = isTaxInclusive, onCheckedChange = { isTaxInclusive = it })
                }
            }
            if (productId == null && !isServiceItem) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = openingStock,
                            onValueChange = { openingStock = it },
                            label = { Text("Opening Stock") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("product_opening_stock")
                        )
                        OutlinedTextField(
                            value = minStock,
                            onValueChange = { minStock = it },
                            label = { Text("Min Stock Alert") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (settings?.enableWholesaleModule == true) {
                item {
                    OutlinedTextField(
                        value = wholesalePrice,
                        onValueChange = { wholesalePrice = it },
                        label = { Text("Wholesale Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (settings?.enableMedicalModule == true) {
                item {
                    OutlinedTextField(value = batchNumber, onValueChange = { batchNumber = it }, label = { Text("Medical Batch Number") }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (settings?.enableGarmentsModule == true) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = garmentSize, onValueChange = { garmentSize = it }, label = { Text("Size") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = garmentColor, onValueChange = { garmentColor = it }, label = { Text("Color") }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (settings?.enableElectronicsModule == true) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = serialNumber, onValueChange = { serialNumber = it }, label = { Text("Serial / IMEI") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = warrantyMonths, onValueChange = { warrantyMonths = it }, label = { Text("Warranty (Mos)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            }
            if (settings?.enableServiceModule == true) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Service Item (No Inventory Stock)")
                        Switch(checked = isServiceItem, onCheckedChange = { isServiceItem = it })
                    }
                }
            }
            if (settings?.enableManufacturingModule == true) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Raw Material for Production")
                        Switch(checked = isRawMaterial, onCheckedChange = { isRawMaterial = it })
                    }
                }
            }
            item {
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        if (name.isBlank()) {
                            scope.launch { snackbar.showSnackbar("Product name is required") }
                            return@Button
                        }
                        val sellingPaise = Money.safeToPaise(Money.of(sellingPrice))
                        if (sellingPaise < 0L) {
                            scope.launch { snackbar.showSnackbar("Selling price cannot be negative") }
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            try {
                                val entity = Product(
                                    id = productId ?: 0L,
                                    name = name.trim(),
                                    imageUri = imageUri,
                                    sku = sku.trim(),
                                    barcode = barcode.trim(),
                                    hsnSacCode = hsn.trim(),
                                    categoryName = categoryName,
                                    brand = brand.trim(),
                                    description = description.trim(),
                                    purchasePricePaise = Money.safeToPaise(Money.of(purchasePrice)).coerceAtLeast(0L),
                                    sellingPricePaise = sellingPaise.coerceAtLeast(0L),
                                    wholesalePricePaise = Money.safeToPaise(Money.of(wholesalePrice)).coerceAtLeast(0L),
                                    mrpPaise = Money.safeToPaise(Money.of(mrp)).coerceAtLeast(0L),
                                    taxRatePercentScaled = Money.safeToPaise(Money.of(taxPercent)).coerceIn(0L, 10_000L),
                                    isTaxInclusive = isTaxInclusive,
                                    unitName = unitName,
                                    openingStockScaled = Money.safeToPaise(Money.of(openingStock)).coerceAtLeast(0L),
                                    minStockScaled = Money.safeToPaise(Money.of(minStock)).coerceAtLeast(0L),
                                    batchNumber = batchNumber.trim(),
                                    garmentSize = garmentSize.trim(),
                                    garmentColor = garmentColor.trim(),
                                    serialNumber = serialNumber.trim(),
                                    warrantyMonths = (warrantyMonths.toIntOrNull() ?: 0).coerceAtLeast(0),
                                    isServiceItem = isServiceItem,
                                    isRawMaterial = isRawMaterial
                                )
                                app.container.inventoryRepository.saveProduct(entity)
                                onSaved()
                            } catch (err: Exception) {
                                snackbar.showSnackbar(err.message ?: "Unable to save product")
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_product_btn")
                ) { Text(if (isSaving) "Saving..." else if (productId == null) "Save Product" else "Update Product") }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerSheet(
            onBarcodeScanned = { barcode = it; showScanner = false },
            onDismiss = { showScanner = false }
        )
    }
}

@Composable
fun ProductDetailScreen(
    productId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAdjustStock: (Long) -> Unit,
    onTransferStock: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val product by app.container.inventoryRepository.observeProduct(productId).collectAsStateWithLifecycle(null)
    val movements by app.container.inventoryRepository.observeProductMovements(productId).collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = product?.name ?: "Product Details",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEdit(productId) }) { Icon(Icons.Default.Edit, contentDescription = "Edit product") }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Archive product") }
                }
            )
        }
    ) { padding ->
        val p = product ?: return@Scaffold
        val marginPaise = p.sellingPricePaise - p.purchasePricePaise
        val stockValue = com.example.data.repository.InventoryRepository.computeStockValuePaise(p.currentStockScaled, p.purchasePricePaise)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.name, style = MaterialTheme.typography.headlineMedium)
                        Text("${p.categoryName} • Unit: ${p.unitName} • GST: ${Money.formatQty(Money.fromPaise(p.taxRatePercentScaled))}%")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Selling Price", style = MaterialTheme.typography.labelSmall)
                                MoneyText(p.sellingPricePaise, currency)
                            }
                            Column {
                                Text("Purchase Price", style = MaterialTheme.typography.labelSmall)
                                MoneyText(p.purchasePricePaise, currency)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Unit Profit", style = MaterialTheme.typography.labelSmall)
                                MoneyText(marginPaise, currency)
                            }
                        }
                        if (!p.isServiceItem) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Stock: ${Money.formatQty(Money.fromPaise(p.currentStockScaled))} ${p.unitName}", fontWeight = FontWeight.Bold)
                                Text("Valuation: ${Money.formatPaise(stockValue, currency)}")
                            }
                        }
                        if (p.barcode.isNotBlank()) {
                            val bmp = remember(p.barcode) { BarcodeUtils.generateCode128Bitmap(p.barcode) }
                            if (bmp != null) {
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Barcode ${p.barcode}", modifier = Modifier.fillMaxWidth().height(72.dp))
                                Text("Barcode: ${p.barcode}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAdjustStock(p.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Tune, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adjust Stock")
                    }
                    OutlinedButton(onClick = { onTransferStock(p.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.SwapHoriz, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transfer")
                    }
                }
            }
            item { Text("Stock Movement History (${movements.size})", style = MaterialTheme.typography.titleMedium) }
            items(movements, key = { it.id }) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.movementType.label, fontWeight = FontWeight.SemiBold)
                            Text("${DateUtils.formatDateTime(m.createdAt)} • ${m.notes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val sign = if (m.quantityDeltaScaled >= 0) "+" else ""
                            Text("$sign${Money.formatQty(Money.fromPaise(m.quantityDeltaScaled))}", fontWeight = FontWeight.Bold)
                            Text("Bal: ${Money.formatQty(Money.fromPaise(m.resultingStockScaled))}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmActionDialog(
            title = "Archive Product?",
            message = "This removes ${product?.name} from active selling lists while preserving all past invoices and stock history.",
            confirmLabel = "Archive",
            onConfirm = {
                scope.launch {
                    app.container.inventoryRepository.deleteProductSafely(productId)
                    showDeleteConfirm = false
                    onBack()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val categories by app.container.businessRepository.categories.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }

    Scaffold(topBar = { HisabPulseTopBar(title = "Product Categories", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New Category") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { app.container.businessRepository.saveCategory(name); name = "" }
                    }
                }) { Text("Add") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { cat ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { scope.launch { app.container.businessRepository.deleteCategory(cat) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete category")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnitsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val units by app.container.businessRepository.units.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var shortName by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("1") }

    Scaffold(topBar = { HisabPulseTopBar(title = "Measurement Units", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Unit Name") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = shortName, onValueChange = { shortName = it.uppercase() }, label = { Text("Short") }, modifier = Modifier.weight(0.6f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = factor, onValueChange = { factor = it }, label = { Text("Conversion Factor") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (name.isNotBlank() && shortName.isNotBlank()) {
                        scope.launch {
                            app.container.businessRepository.saveUnit(
                                UnitEntity(name = name.trim(), shortName = shortName.trim(), conversionFactorScaled = Money.safeToPaise(Money.of(factor)))
                            )
                            name = ""; shortName = ""; factor = "1"
                        }
                    }
                }) { Text("Add Unit") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(units, key = { it.id }) { u ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${u.name} (${u.shortName})", style = MaterialTheme.typography.titleMedium)
                                Text("Factor: ${Money.formatQty(Money.fromPaise(u.conversionFactorScaled))}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { scope.launch { app.container.businessRepository.deleteUnit(u) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete unit")
                            }
                        }
                    }
                }
            }
        }
    }
}
