package com.example.ui.screens.dataops

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.core.TabularImportData
import com.example.data.local.entities.DuplicateStrategy
import com.example.data.repository.ImportPreviewSummary
import com.example.data.repository.ImportTargetType
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val importHistory by app.container.recoveryRepository.importHistory.collectAsStateWithLifecycle(emptyList())

    var selectedFileName by remember { mutableStateOf("") }
    var parsedTable by remember { mutableStateOf<TabularImportData?>(null) }
    var targetType by remember { mutableStateOf(ImportTargetType.PRODUCTS) }
    var columnMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var duplicateStrategy by remember { mutableStateOf(DuplicateStrategy.SKIP) }
    var previewSummary by remember { mutableStateOf<ImportPreviewSummary?>(null) }
    var saveTemplateName by remember { mutableStateOf("") }

    fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "import.csv"
        }
        return uri.lastPathSegment ?: "import.csv"
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val fName = queryDisplayName(uri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    snackbar.showSnackbar("Unable to read selected file")
                    return@launch
                }
                app.container.recoveryRepository.parseTabularBytes(fName, bytes)
                    .onSuccess { tbl ->
                        selectedFileName = fName
                        parsedTable = tbl
                        columnMap = app.container.recoveryRepository.autoSuggestColumnMapping(tbl.headers, targetType)
                        previewSummary = null
                        snackbar.showSnackbar("Loaded ${tbl.rows.size} rows from ${tbl.sourceFormat}")
                    }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Failed to parse CSV/XLSX") }
            }
        }
    }

    LaunchedEffect(parsedTable, targetType, columnMap) {
        val tbl = parsedTable ?: return@LaunchedEffect
        previewSummary = app.container.recoveryRepository.previewAndValidateTabularImport(tbl, targetType, columnMap)
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "CSV / Excel Import & Export",
                subtitle = "Guided column mapping, validation & undo",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Step 1 • Select Target & Upload CSV / XLSX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImportTargetType.entries.forEach { tgt ->
                                FilterChip(
                                    selected = targetType == tgt,
                                    onClick = {
                                        targetType = tgt
                                        parsedTable?.let { tbl ->
                                            columnMap = app.container.recoveryRepository.autoSuggestColumnMapping(tbl.headers, tgt)
                                        }
                                    },
                                    label = { Text(tgt.label) }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                openLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_csv_xlsx_btn")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(if (selectedFileName.isBlank()) "Choose .CSV or .XLSX File" else "Selected: $selectedFileName")
                        }
                    }
                }
            }

            val tbl = parsedTable
            if (tbl != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Step 2 • Map Columns (${tbl.rows.size} rows)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val allFields = targetType.requiredFields + targetType.optionalFields
                            allFields.forEach { field ->
                                val isReq = field in targetType.requiredFields
                                val mappedIdx = columnMap[field]
                                var expanded by remember(field, tbl.headers) { mutableStateOf(false) }

                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = mappedIdx?.let { tbl.headers.getOrNull(it) } ?: "Not Mapped",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("${field}${if (isReq) " *" else ""}") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor()
                                    )
                                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("None / Skip") },
                                            onClick = {
                                                columnMap = columnMap - field
                                                expanded = false
                                            }
                                        )
                                        tbl.headers.forEachIndexed { idx, colName ->
                                            DropdownMenuItem(
                                                text = { Text("Col ${idx + 1}: $colName") },
                                                onClick = {
                                                    columnMap = columnMap + (field to idx)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Step 3 • Preview & Duplicate Rule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(DuplicateStrategy.SKIP, DuplicateStrategy.UPDATE).forEach { strat ->
                                    FilterChip(
                                        selected = duplicateStrategy == strat,
                                        onClick = { duplicateStrategy = strat },
                                        label = { Text(strat.label) }
                                    )
                                }
                            }
                            previewSummary?.let { prev ->
                                Text(
                                    "Ready: ${prev.validRowIndices.size} valid • ${prev.duplicateRowIndices.size} duplicates • ${prev.errors.size} errors",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (prev.errors.isEmpty()) semantic.success else semantic.warning
                                )
                                prev.errors.take(5).forEach { err ->
                                    Text("Row ${err.rowNumber}: ${err.message}", style = MaterialTheme.typography.bodySmall, color = semantic.danger)
                                }
                            }
                            OutlinedTextField(
                                value = saveTemplateName,
                                onValueChange = { saveTemplateName = it },
                                label = { Text("Save Mapping Template Name (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.container.recoveryRepository.executeTabularImport(
                                            sourceFileName = selectedFileName,
                                            table = tbl,
                                            targetType = targetType,
                                            columnMap = columnMap,
                                            duplicateStrategy = duplicateStrategy,
                                            saveMappingName = saveTemplateName
                                        ).onSuccess { res ->
                                            parsedTable = null
                                            selectedFileName = ""
                                            snackbar.showSnackbar("Imported ${res.importedCount}, updated ${res.updatedCount}, skipped ${res.skippedCount}")
                                        }.onFailure { snackbar.showSnackbar(it.message ?: "Import failed") }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("execute_import_btn")
                            ) {
                                Text("Execute Safe Import (Creates Pre-Import Snapshot)")
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick Data Exports (.XLSX & .CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val prods = app.container.inventoryRepository.activeProducts.firstOrNull().orEmpty()
                                        val file = withContext(Dispatchers.IO) {
                                            val headers = listOf("Name", "SKU", "Barcode", "HSN", "Category", "Unit", "PurchasePrice", "SellingPrice", "MRP", "GST%", "Stock")
                                            val rows = prods.map {
                                                listOf(
                                                    it.name,
                                                    it.sku,
                                                    it.barcode,
                                                    it.hsnSacCode,
                                                    it.categoryName,
                                                    it.unitName,
                                                    Money.formatPlain(Money.fromPaise(it.purchasePricePaise)),
                                                    Money.formatPlain(Money.fromPaise(it.sellingPricePaise)),
                                                    Money.formatPlain(Money.fromPaise(it.mrpPaise)),
                                                    Money.formatPlain(Money.fromPaise(it.taxRatePercentScaled)),
                                                    Money.formatPlain(Money.fromPaise(it.currentStockScaled))
                                                )
                                            }
                                            PdfGenerator.exportXlsx(context, "HisabPulse_Products.xlsx", "Products", headers, rows)
                                        }
                                        PdfGenerator.shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Export Products Excel")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.padding(2.dp))
                                Text("Products .XLSX")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val custs = app.container.partyRepository.customers.firstOrNull().orEmpty()
                                        val file = withContext(Dispatchers.IO) {
                                            val headers = listOf("Name", "Phone", "Email", "Address", "State", "GSTIN", "Balance")
                                            val rows = custs.map {
                                                listOf(it.name, it.phone, it.email, it.address, it.state, it.gstin, Money.formatPlain(Money.fromPaise(it.currentBalancePaise)))
                                            }
                                            PdfGenerator.exportCsv(context, "HisabPulse_Customers.csv", headers, rows)
                                        }
                                        PdfGenerator.shareFile(context, file, "text/csv", "Export Customers CSV")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.padding(2.dp))
                                Text("Customers .CSV")
                            }
                        }
                    }
                }
            }

            item { Text("Import History & Undo (${importHistory.size})", style = MaterialTheme.typography.titleMedium) }
            items(importHistory, key = { it.id }) { h ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${h.sourceFileName} (${h.targetEntityType})", fontWeight = FontWeight.Bold)
                            Text("Imported ${h.importedCount} • Updated ${h.updatedCount} • Skipped ${h.skippedCount}", style = MaterialTheme.typography.bodySmall)
                            Text(DateUtils.formatDateTime(h.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (h.wasUndone) {
                            StatusPill("Undone", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (h.importedCount > 0) {
                            TextButton(onClick = {
                                scope.launch {
                                    app.container.recoveryRepository.undoRecentImport(h)
                                        .onSuccess { snackbar.showSnackbar("Undid newly imported records from ${h.sourceFileName}") }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Undo failed") }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                                Spacer(Modifier.padding(2.dp))
                                Text("Undo")
                            }
                        }
                    }
                }
            }
        }
    }
}
