package com.example.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.example.core.ExcelXmlUtils
import com.example.core.FileInspectionReport
import com.example.core.InspectedFileFormat
import com.example.core.Money
import com.example.core.SecurityManager
import com.example.core.TabularImportData
import com.example.core.UniversalFileInspector
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Customer
import com.example.data.local.entities.DuplicateStrategy
import com.example.data.local.entities.ImportHistory
import com.example.data.local.entities.ImportMapping
import com.example.data.local.entities.Product
import com.example.data.local.entities.RecoveryHistory
import com.example.data.local.entities.RecoveryMode
import com.example.data.local.entities.RecoveryRecord
import com.example.data.local.entities.Supplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal

enum class ImportTargetType(val label: String, val requiredFields: List<String>, val optionalFields: List<String>) {
    PRODUCTS("Products", listOf("name", "sellingPrice"), listOf("purchasePrice", "mrp", "barcode", "hsn", "taxPercent", "stock", "unit", "category")),
    CUSTOMERS("Customers", listOf("name"), listOf("phone", "email", "address", "state", "gstin", "openingBalance")),
    SUPPLIERS("Suppliers", listOf("name"), listOf("phone", "email", "address", "state", "gstin", "openingBalance")),
    OPENING_STOCK("Opening Stock", listOf("name", "stock"), listOf("barcode", "purchasePrice"))
}

data class ImportValidationError(
    val rowNumber: Int,
    val message: String
)

data class ImportPreviewSummary(
    val targetType: ImportTargetType,
    val totalRows: Int,
    val validRowIndices: List<Int>,
    val duplicateRowIndices: List<Int>,
    val errors: List<ImportValidationError>
)

data class UniversalRecoveryResult(
    val recoveryHistoryId: Long,
    val status: String,
    val recoveredCount: Int,
    val unsupportedCount: Int,
    val errorCount: Int,
    val safetyBackupFile: String,
    val summary: String,
    val records: List<RecoveryRecord>
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryAndImportRepository(
    private val context: Context,
    private val db: HisabPulseDatabase,
    private val backupRepository: BackupRepository,
    private val businessRepository: BusinessRepository
) {
    private val coreDao = db.coreDao()
    private val inventoryDao = db.inventoryDao()
    private val partyDao = db.partyDao()
    private val advancedDao = db.advancedDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val importHistory: Flow<List<ImportHistory>> = activeBizFlow.flatMapLatest { advancedDao.observeImportHistory(it) }
    val recoveryHistory: Flow<List<RecoveryHistory>> = activeBizFlow.flatMapLatest { advancedDao.observeRecoveryHistory(it) }
    val savedMappings: Flow<List<ImportMapping>> = activeBizFlow.flatMapLatest { advancedDao.observeImportMappings(it) }

    fun observeRecoveryRecords(recoveryId: Long): Flow<List<RecoveryRecord>> =
        advancedDao.observeRecoveryRecords(recoveryId)

    // ── CSV & XLSX Parsing & Auto-Mapping ──

    fun parseTabularBytes(fileName: String, bytes: ByteArray): Result<TabularImportData> = runCatching {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (ext == "xlsx" || isZip) {
            ExcelXmlUtils.parseXlsx(bytes)
        } else {
            ExcelXmlUtils.parseCsv(String(bytes, Charsets.UTF_8))
        }
    }

    fun autoSuggestColumnMapping(headers: List<String>, targetType: ImportTargetType): Map<String, Int> {
        val normalized = headers.mapIndexed { idx, h ->
            idx to h.lowercase().replace(Regex("[^a-z0-9]"), "")
        }
        fun findCol(vararg aliases: String): Int? =
            normalized.firstOrNull { (_, h) -> aliases.any { h.contains(it) } }?.first

        val map = mutableMapOf<String, Int>()
        when (targetType) {
            ImportTargetType.PRODUCTS -> {
                findCol("productname", "itemname", "name", "item", "title")?.let { map["name"] = it }
                findCol("selling", "saleprice", "rate", "price")?.let { map["sellingPrice"] = it }
                findCol("purchase", "cost", "buyprice")?.let { map["purchasePrice"] = it }
                findCol("mrp")?.let { map["mrp"] = it }
                findCol("barcode", "ean", "upc")?.let { map["barcode"] = it }
                findCol("hsn", "sac")?.let { map["hsn"] = it }
                findCol("gst", "tax")?.let { map["taxPercent"] = it }
                findCol("stock", "qty", "quantity", "opening")?.let { map["stock"] = it }
                findCol("unit", "uom")?.let { map["unit"] = it }
                findCol("category", "group")?.let { map["category"] = it }
            }
            ImportTargetType.CUSTOMERS, ImportTargetType.SUPPLIERS -> {
                findCol("partyname", "customername", "suppliername", "name")?.let { map["name"] = it }
                findCol("phone", "mobile", "contact")?.let { map["phone"] = it }
                findCol("email", "mail")?.let { map["email"] = it }
                findCol("address", "addr")?.let { map["address"] = it }
                findCol("state")?.let { map["state"] = it }
                findCol("gstin", "gst")?.let { map["gstin"] = it }
                findCol("opening", "balance", "due")?.let { map["openingBalance"] = it }
            }
            ImportTargetType.OPENING_STOCK -> {
                findCol("productname", "itemname", "name")?.let { map["name"] = it }
                findCol("barcode", "sku")?.let { map["barcode"] = it }
                findCol("stock", "qty", "quantity", "opening")?.let { map["stock"] = it }
                findCol("purchase", "cost")?.let { map["purchasePrice"] = it }
            }
        }
        return map
    }

    suspend fun previewAndValidateTabularImport(
        table: TabularImportData,
        targetType: ImportTargetType,
        columnMap: Map<String, Int>
    ): ImportPreviewSummary {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val validIndices = mutableListOf<Int>()
        val dupIndices = mutableListOf<Int>()
        val errors = mutableListOf<ImportValidationError>()

        fun cell(row: List<String>, field: String): String {
            val idx = columnMap[field] ?: return ""
            return row.getOrNull(idx)?.trim().orEmpty()
        }

        table.rows.forEachIndexed { rIdx, row ->
            val rowNo = rIdx + 2
            val name = cell(row, "name")
            if (name.isBlank()) {
                errors += ImportValidationError(rowNo, "Missing required Name")
                return@forEachIndexed
            }
            when (targetType) {
                ImportTargetType.PRODUCTS -> {
                    val sellStr = cell(row, "sellingPrice")
                    val sell = sellStr.toBigDecimalOrNull()
                    if (sellStr.isNotBlank() && (sell == null || sell < BigDecimal.ZERO)) {
                        errors += ImportValidationError(rowNo, "Invalid selling price '$sellStr'")
                        return@forEachIndexed
                    }
                    val barcode = cell(row, "barcode")
                    val existing = (if (barcode.isNotBlank()) inventoryDao.findProductByBarcode(barcode, bizId) else null)
                        ?: inventoryDao.findProductByName(name, bizId)
                    if (existing != null) dupIndices += rIdx
                    validIndices += rIdx
                }
                ImportTargetType.CUSTOMERS -> {
                    val phone = cell(row, "phone")
                    val existing = (if (phone.isNotBlank()) partyDao.findCustomerByPhone(phone, bizId) else null)
                        ?: partyDao.findCustomerByName(name, bizId)
                    if (existing != null) dupIndices += rIdx
                    validIndices += rIdx
                }
                ImportTargetType.SUPPLIERS -> {
                    val existing = partyDao.findSupplierByName(name, bizId)
                    if (existing != null) dupIndices += rIdx
                    validIndices += rIdx
                }
                ImportTargetType.OPENING_STOCK -> {
                    val qtyStr = cell(row, "stock")
                    val qty = qtyStr.toBigDecimalOrNull()
                    if (qty == null) {
                        errors += ImportValidationError(rowNo, "Invalid stock quantity '$qtyStr'")
                        return@forEachIndexed
                    }
                    val barcode = cell(row, "barcode")
                    val existing = (if (barcode.isNotBlank()) inventoryDao.findProductByBarcode(barcode, bizId) else null)
                        ?: inventoryDao.findProductByName(name, bizId)
                    if (existing == null) {
                        errors += ImportValidationError(rowNo, "Product '$name' not found for stock update")
                        return@forEachIndexed
                    }
                    dupIndices += rIdx
                    validIndices += rIdx
                }
            }
        }

        return ImportPreviewSummary(
            targetType = targetType,
            totalRows = table.rows.size,
            validRowIndices = validIndices,
            duplicateRowIndices = dupIndices,
            errors = errors
        )
    }

    suspend fun executeTabularImport(
        sourceFileName: String,
        table: TabularImportData,
        targetType: ImportTargetType,
        columnMap: Map<String, Int>,
        duplicateStrategy: DuplicateStrategy,
        saveMappingName: String = ""
    ): Result<ImportHistory> = withContext(Dispatchers.IO) {
        runCatching {
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            val safetyFile = backupRepository.createSafetySnapshotFile("pre_import")
            val safetyChecksum = SecurityManager.sha256Hex(safetyFile.readBytes())

            if (saveMappingName.isNotBlank()) {
                advancedDao.insertImportMapping(
                    ImportMapping(
                        businessId = bizId,
                        name = saveMappingName.trim(),
                        targetType = targetType.name,
                        columnMapJson = json.encodeToString(columnMap)
                    )
                )
            }

            fun cell(row: List<String>, field: String): String {
                val idx = columnMap[field] ?: return ""
                return row.getOrNull(idx)?.trim().orEmpty()
            }

            var imported = 0
            var updated = 0
            var skipped = 0
            var errors = 0
            val createdIds = mutableListOf<Long>()

            db.withTransaction {
                for (row in table.rows) {
                    val name = cell(row, "name")
                    if (name.isBlank()) {
                        errors++
                        continue
                    }
                    when (targetType) {
                        ImportTargetType.PRODUCTS -> {
                            val sell = cell(row, "sellingPrice").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val pur = cell(row, "purchasePrice").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val mrp = cell(row, "mrp").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val tax = cell(row, "taxPercent").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val stock = cell(row, "stock").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val barcode = cell(row, "barcode")
                            val hsn = cell(row, "hsn")
                            val unit = cell(row, "unit").ifBlank { "PCS" }.uppercase()
                            val cat = cell(row, "category").ifBlank { "General" }

                            val existing = (if (barcode.isNotBlank()) inventoryDao.findProductByBarcode(barcode, bizId) else null)
                                ?: inventoryDao.findProductByName(name, bizId)
                            if (existing != null) {
                                if (duplicateStrategy == DuplicateStrategy.UPDATE) {
                                    inventoryDao.updateProduct(
                                        existing.copy(
                                            sellingPricePaise = if (sell > BigDecimal.ZERO) Money.safeToPaise(sell) else existing.sellingPricePaise,
                                            purchasePricePaise = if (pur > BigDecimal.ZERO) Money.safeToPaise(pur) else existing.purchasePricePaise,
                                            mrpPaise = if (mrp > BigDecimal.ZERO) Money.safeToPaise(mrp) else existing.mrpPaise,
                                            hsnSacCode = hsn.ifBlank { existing.hsnSacCode },
                                            taxRatePercentScaled = Money.safeToPaise(tax),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    updated++
                                } else {
                                    skipped++
                                }
                            } else {
                                val newId = inventoryDao.insertProduct(
                                    Product(
                                        businessId = bizId,
                                        name = name,
                                        barcode = barcode,
                                        hsnSacCode = hsn,
                                        categoryName = cat,
                                        unitName = unit,
                                        purchasePricePaise = Money.safeToPaise(pur),
                                        sellingPricePaise = Money.safeToPaise(sell),
                                        mrpPaise = Money.safeToPaise(mrp),
                                        taxRatePercentScaled = Money.safeToPaise(tax),
                                        openingStockScaled = Money.safeToPaise(stock),
                                        currentStockScaled = Money.safeToPaise(stock)
                                    )
                                )
                                createdIds += newId
                                imported++
                            }
                        }
                        ImportTargetType.CUSTOMERS -> {
                            val phone = cell(row, "phone")
                            val email = cell(row, "email")
                            val address = cell(row, "address")
                            val state = cell(row, "state")
                            val gstin = cell(row, "gstin").uppercase()
                            val openBal = cell(row, "openingBalance").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val existing = (if (phone.isNotBlank()) partyDao.findCustomerByPhone(phone, bizId) else null)
                                ?: partyDao.findCustomerByName(name, bizId)
                            if (existing != null) {
                                if (duplicateStrategy == DuplicateStrategy.UPDATE) {
                                    partyDao.updateCustomer(
                                        existing.copy(
                                            phone = phone.ifBlank { existing.phone },
                                            email = email.ifBlank { existing.email },
                                            address = address.ifBlank { existing.address },
                                            state = state.ifBlank { existing.state },
                                            gstin = gstin.ifBlank { existing.gstin },
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    updated++
                                } else {
                                    skipped++
                                }
                            } else {
                                val balPaise = Money.safeToPaise(openBal)
                                val newId = partyDao.insertCustomer(
                                    Customer(
                                        businessId = bizId,
                                        name = name,
                                        phone = phone,
                                        email = email,
                                        address = address,
                                        state = state,
                                        gstin = gstin,
                                        openingBalancePaise = balPaise,
                                        currentBalancePaise = balPaise
                                    )
                                )
                                createdIds += newId
                                imported++
                            }
                        }
                        ImportTargetType.SUPPLIERS -> {
                            val phone = cell(row, "phone")
                            val email = cell(row, "email")
                            val address = cell(row, "address")
                            val state = cell(row, "state")
                            val gstin = cell(row, "gstin").uppercase()
                            val openBal = cell(row, "openingBalance").toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val existing = partyDao.findSupplierByName(name, bizId)
                            if (existing != null) {
                                if (duplicateStrategy == DuplicateStrategy.UPDATE) {
                                    partyDao.updateSupplier(
                                        existing.copy(
                                            phone = phone.ifBlank { existing.phone },
                                            email = email.ifBlank { existing.email },
                                            address = address.ifBlank { existing.address },
                                            state = state.ifBlank { existing.state },
                                            gstin = gstin.ifBlank { existing.gstin },
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    updated++
                                } else {
                                    skipped++
                                }
                            } else {
                                val balPaise = Money.safeToPaise(openBal)
                                val newId = partyDao.insertSupplier(
                                    Supplier(
                                        businessId = bizId,
                                        name = name,
                                        phone = phone,
                                        email = email,
                                        address = address,
                                        state = state,
                                        gstin = gstin,
                                        openingBalancePaise = balPaise,
                                        currentBalancePaise = balPaise
                                    )
                                )
                                createdIds += newId
                                imported++
                            }
                        }
                        ImportTargetType.OPENING_STOCK -> {
                            val barcode = cell(row, "barcode")
                            val qty = cell(row, "stock").toBigDecimalOrNull()
                            val pur = cell(row, "purchasePrice").toBigDecimalOrNull()
                            val existing = (if (barcode.isNotBlank()) inventoryDao.findProductByBarcode(barcode, bizId) else null)
                                ?: inventoryDao.findProductByName(name, bizId)
                            if (existing != null && qty != null) {
                                val qtyScaled = Money.safeToPaise(qty)
                                inventoryDao.updateProduct(
                                    existing.copy(
                                        currentStockScaled = qtyScaled,
                                        purchasePricePaise = if (pur != null && pur > BigDecimal.ZERO) Money.safeToPaise(pur) else existing.purchasePricePaise,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                updated++
                            } else {
                                errors++
                            }
                        }
                    }
                }
            }

            val history = ImportHistory(
                businessId = bizId,
                sourceFileName = sourceFileName,
                detectedFormat = table.sourceFormat,
                targetEntityType = targetType.name,
                duplicateStrategy = duplicateStrategy,
                importedCount = imported,
                updatedCount = updated,
                skippedCount = skipped,
                errorCount = errors,
                safetyBackupChecksum = safetyChecksum,
                createdRecordIdsJson = json.encodeToString(createdIds)
            )
            val hid = advancedDao.insertImportHistory(history)
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "IMPORT_TABULAR",
                    entityType = targetType.name,
                    entityId = hid,
                    summary = "Imported $imported, updated $updated, skipped $skipped from $sourceFileName"
                )
            )
            history.copy(id = hid)
        }
    }

    suspend fun undoRecentImport(history: ImportHistory): Result<Unit> = runCatching {
        require(!history.wasUndone) { "This import was already undone" }
        val ids = runCatching { json.decodeFromString<List<Long>>(history.createdRecordIdsJson) }.getOrDefault(emptyList())
        db.withTransaction {
            when (history.targetEntityType) {
                ImportTargetType.PRODUCTS.name -> ids.forEach { inventoryDao.softDeleteProduct(it) }
                ImportTargetType.CUSTOMERS.name -> ids.forEach { id ->
                    partyDao.getCustomerByIdOnce(id)?.let { partyDao.updateCustomer(it.copy(isActive = false)) }
                }
                ImportTargetType.SUPPLIERS.name -> ids.forEach { id ->
                    partyDao.getSupplierByIdOnce(id)?.let { partyDao.updateSupplier(it.copy(isActive = false)) }
                }
            }
            advancedDao.updateImportHistory(history.copy(wasUndone = true))
        }
    }

    // ── Universal Data Recovery Center ──

    fun inspectRecoveryFile(fileName: String, bytes: ByteArray): FileInspectionReport =
        UniversalFileInspector.inspect(fileName, bytes)

    suspend fun executeUniversalRecovery(
        fileName: String,
        bytes: ByteArray,
        passphrase: String,
        mode: RecoveryMode,
        duplicateStrategy: DuplicateStrategy,
        selectedCategories: Set<String> = setOf("PRODUCTS", "CUSTOMERS", "SUPPLIERS", "INVOICES", "EXPENSES"),
        newBusinessName: String = ""
    ): Result<UniversalRecoveryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val report = UniversalFileInspector.inspect(fileName, bytes)
            val currentBizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            val safetyFile = backupRepository.createSafetySnapshotFile("pre_recovery")
            val recoveryRecords = mutableListOf<RecoveryRecord>()
            var recoveredCount = 0
            var unsupportedCount = 0
            var errorCount = 0
            var summary = ""

            when (report.detectedFormat) {
                InspectedFileFormat.HISABP_ENCRYPTED, InspectedFileFormat.JSON_BACKUP -> {
                    val snapshot = backupRepository.decodeAndValidateBackupBytes(bytes, passphrase)
                    when (mode) {
                        RecoveryMode.FULL_RESTORE -> {
                            backupRepository.restoreFromSnapshot(snapshot).getOrThrow()
                            recoveredCount = snapshot.customers.size + snapshot.suppliers.size +
                                snapshot.products.size + snapshot.invoices.size + snapshot.expenses.size
                            summary = "Full atomic restore completed ($recoveredCount business records)."
                        }
                        RecoveryMode.REPAIR_VALIDATE -> {
                            val totalDebit = snapshot.ledgerEntries.sumOf { it.debitPaise }
                            val totalCredit = snapshot.ledgerEntries.sumOf { it.creditPaise }
                            recoveredCount = snapshot.invoices.size + snapshot.products.size + snapshot.customers.size
                            summary = "Validated backup integrity: schema v${snapshot.schemaVersion}, ledger balance Debit=$totalDebit vs Credit=$totalCredit."
                        }
                        RecoveryMode.NEW_BUSINESS_IMPORT -> {
                            val bName = newBusinessName.ifBlank {
                                (snapshot.businessProfile?.businessName ?: "Imported Business") + " (Recovered)"
                            }
                            val targetBizId = businessRepository.createBusinessProfile(
                                (snapshot.businessProfile ?: BusinessProfile(businessName = bName)).copy(
                                    id = 0L,
                                    businessName = bName
                                ),
                                switchToNewBusiness = true
                            )
                            val mergeRes = mergeSnapshotIntoBusiness(
                                snapshot, targetBizId, duplicateStrategy, selectedCategories, recoveryRecords
                            )
                            recoveredCount = mergeRes
                            summary = "Imported $recoveredCount records into new business '$bName'."
                        }
                        RecoveryMode.MERGE_IMPORT, RecoveryMode.SELECTIVE_RECOVERY, RecoveryMode.MIGRATION_IMPORT -> {
                            recoveredCount = mergeSnapshotIntoBusiness(
                                snapshot, currentBizId, duplicateStrategy, selectedCategories, recoveryRecords
                            )
                            summary = "Safely merged $recoveredCount records without wiping existing business data."
                        }
                    }
                }
                InspectedFileFormat.CSV_SPREADSHEET, InspectedFileFormat.XLSX_SPREADSHEET -> {
                    val table = parseTabularBytes(fileName, bytes).getOrThrow()
                    val guessedTarget = guessTargetTypeFromHeaders(table.headers)
                    val mapping = autoSuggestColumnMapping(table.headers, guessedTarget)
                    val importRes = executeTabularImport(
                        sourceFileName = fileName,
                        table = table,
                        targetType = guessedTarget,
                        columnMap = mapping,
                        duplicateStrategy = duplicateStrategy
                    ).getOrThrow()
                    recoveredCount = importRes.importedCount + importRes.updatedCount
                    errorCount = importRes.errorCount
                    summary = "Recovered $recoveredCount ${guessedTarget.label} rows from ${table.sourceFormat} (${importRes.skippedCount} skipped, $errorCount errors)."
                }
                InspectedFileFormat.SQLITE_DATABASE -> {
                    val stats = recoverFromSqliteBytes(bytes, currentBizId, duplicateStrategy, recoveryRecords)
                    recoveredCount = stats.first
                    unsupportedCount = stats.second
                    summary = "Extracted $recoveredCount compatible records from SQLite database ($unsupportedCount unmapped rows)."
                }
                InspectedFileFormat.VYP_CONTAINER -> {
                    if (!report.canDirectlyRecover) {
                        unsupportedCount = 1
                        recoveryRecords += RecoveryRecord(
                            recoveryHistoryId = 0L,
                            recordCategory = "UNSUPPORTED",
                            sourceIdentifier = fileName,
                            status = "UNSUPPORTED",
                            message = report.honestLimitationNote.ifBlank { report.summaryMessage }
                        )
                        summary = report.summaryMessage
                    } else {
                        val files = UniversalFileInspector.extractZipFiles(bytes).getOrThrow()
                        files.entries.forEach { (entryName, entryBytes) ->
                            val lower = entryName.lowercase()
                            when {
                                lower.endsWith(".csv") || lower.endsWith(".xlsx") -> {
                                    val tbl = parseTabularBytes(entryName, entryBytes).getOrNull()
                                    if (tbl != null && tbl.headers.isNotEmpty()) {
                                        val tgt = guessTargetTypeFromHeaders(tbl.headers)
                                        val map = autoSuggestColumnMapping(tbl.headers, tgt)
                                        val imp = executeTabularImport(entryName, tbl, tgt, map, duplicateStrategy).getOrNull()
                                        if (imp != null) recoveredCount += imp.importedCount + imp.updatedCount
                                    }
                                }
                                lower.endsWith(".db") || lower.endsWith(".sqlite") -> {
                                    val stats = recoverFromSqliteBytes(entryBytes, currentBizId, duplicateStrategy, recoveryRecords)
                                    recoveredCount += stats.first
                                    unsupportedCount += stats.second
                                }
                                else -> unsupportedCount++
                            }
                        }
                        summary = "Recovered $recoveredCount readable items from VYP container ($unsupportedCount skipped/unsupported)."
                    }
                }
                InspectedFileFormat.ZIP_ARCHIVE -> {
                    val files = UniversalFileInspector.extractZipFiles(bytes).getOrThrow()
                    files.entries.forEach { (entryName, entryBytes) ->
                        val subReport = UniversalFileInspector.inspect(entryName, entryBytes)
                        when (subReport.detectedFormat) {
                            InspectedFileFormat.HISABP_ENCRYPTED, InspectedFileFormat.JSON_BACKUP -> {
                                val snap = runCatching { backupRepository.decodeAndValidateBackupBytes(entryBytes, passphrase) }.getOrNull()
                                if (snap != null) {
                                    recoveredCount += mergeSnapshotIntoBusiness(
                                        snap, currentBizId, duplicateStrategy, selectedCategories, recoveryRecords
                                    )
                                } else errorCount++
                            }
                            InspectedFileFormat.CSV_SPREADSHEET, InspectedFileFormat.XLSX_SPREADSHEET -> {
                                val tbl = parseTabularBytes(entryName, entryBytes).getOrNull()
                                if (tbl != null && tbl.headers.isNotEmpty()) {
                                    val tgt = guessTargetTypeFromHeaders(tbl.headers)
                                    val map = autoSuggestColumnMapping(tbl.headers, tgt)
                                    val imp = executeTabularImport(entryName, tbl, tgt, map, duplicateStrategy).getOrNull()
                                    if (imp != null) recoveredCount += imp.importedCount + imp.updatedCount
                                }
                            }
                            else -> unsupportedCount++
                        }
                    }
                    summary = "Processed ZIP archive: $recoveredCount records recovered, $unsupportedCount non-data entries."
                }
                else -> {
                    unsupportedCount = 1
                    recoveryRecords += RecoveryRecord(
                        recoveryHistoryId = 0L,
                        recordCategory = "UNSUPPORTED",
                        sourceIdentifier = fileName,
                        status = "UNSUPPORTED",
                        message = report.summaryMessage
                    )
                    summary = report.summaryMessage
                }
            }

            val finalStatus = when {
                errorCount > 0 && recoveredCount == 0 -> "FAILED"
                unsupportedCount > 0 && recoveredCount == 0 -> "UNSUPPORTED"
                unsupportedCount > 0 || errorCount > 0 -> "PARTIAL"
                else -> "COMPLETED"
            }

            val historyId = advancedDao.insertRecoveryHistory(
                RecoveryHistory(
                    businessId = currentBizId,
                    sourceFileName = fileName,
                    detectedFileType = report.detectedFormat.name,
                    recoveryMode = mode,
                    duplicateStrategy = duplicateStrategy,
                    safetyBackupFile = safetyFile.name,
                    recoveredCount = recoveredCount,
                    unsupportedCount = unsupportedCount,
                    errorCount = errorCount,
                    status = finalStatus,
                    reportSummary = summary
                )
            )
            if (recoveryRecords.isNotEmpty()) {
                advancedDao.insertRecoveryRecords(recoveryRecords.map { it.copy(recoveryHistoryId = historyId) })
            }
            UniversalRecoveryResult(
                recoveryHistoryId = historyId,
                status = finalStatus,
                recoveredCount = recoveredCount,
                unsupportedCount = unsupportedCount,
                errorCount = errorCount,
                safetyBackupFile = safetyFile.name,
                summary = summary,
                records = recoveryRecords
            )
        }
    }

    private suspend fun mergeSnapshotIntoBusiness(
        snapshot: FullBackupSnapshot,
        targetBizId: Long,
        duplicateStrategy: DuplicateStrategy,
        categories: Set<String>,
        recordsOut: MutableList<RecoveryRecord>
    ): Int {
        var count = 0
        db.withTransaction {
            if ("CUSTOMERS" in categories) {
                for (c in snapshot.customers) {
                    val existing = (if (c.phone.isNotBlank()) partyDao.findCustomerByPhone(c.phone, targetBizId) else null)
                        ?: partyDao.findCustomerByName(c.name, targetBizId)
                    if (existing != null) {
                        if (duplicateStrategy == DuplicateStrategy.UPDATE) {
                            partyDao.updateCustomer(existing.copy(phone = c.phone.ifBlank { existing.phone }, address = c.address.ifBlank { existing.address }, gstin = c.gstin.ifBlank { existing.gstin }))
                            count++
                            recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "CUSTOMER", sourceIdentifier = c.name, mappedLocalId = existing.id, status = "UPDATED")
                        } else {
                            recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "CUSTOMER", sourceIdentifier = c.name, mappedLocalId = existing.id, status = "SKIPPED")
                        }
                    } else {
                        val newId = partyDao.insertCustomer(c.copy(id = 0L, businessId = targetBizId))
                        count++
                        recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "CUSTOMER", sourceIdentifier = c.name, mappedLocalId = newId, status = "IMPORTED")
                    }
                }
            }
            if ("SUPPLIERS" in categories) {
                for (s in snapshot.suppliers) {
                    val existing = partyDao.findSupplierByName(s.name, targetBizId)
                    if (existing != null) {
                        if (duplicateStrategy == DuplicateStrategy.UPDATE) {
                            partyDao.updateSupplier(existing.copy(phone = s.phone.ifBlank { existing.phone }, gstin = s.gstin.ifBlank { existing.gstin }))
                            count++
                            recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "SUPPLIER", sourceIdentifier = s.name, mappedLocalId = existing.id, status = "UPDATED")
                        } else {
                            recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "SUPPLIER", sourceIdentifier = s.name, mappedLocalId = existing.id, status = "SKIPPED")
                        }
                    } else {
                        val newId = partyDao.insertSupplier(s.copy(id = 0L, businessId = targetBizId))
                        count++
                        recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "SUPPLIER", sourceIdentifier = s.name, mappedLocalId = newId, status = "IMPORTED")
                    }
                }
            }
            if ("PRODUCTS" in categories) {
                for (p in snapshot.products) {
                    val existing = (if (p.barcode.isNotBlank()) inventoryDao.findProductByBarcode(p.barcode, targetBizId) else null)
                        ?: inventoryDao.findProductByName(p.name, targetBizId)
                    if (existing != null) {
                        if (duplicateStrategy == DuplicateStrategy.UPDATE) {
                            inventoryDao.updateProduct(existing.copy(sellingPricePaise = p.sellingPricePaise, purchasePricePaise = p.purchasePricePaise, mrpPaise = p.mrpPaise))
                            count++
                            recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "PRODUCT", sourceIdentifier = p.name, mappedLocalId = existing.id, status = "UPDATED")
                        } else {
                            recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "PRODUCT", sourceIdentifier = p.name, mappedLocalId = existing.id, status = "SKIPPED")
                        }
                    } else {
                        val newId = inventoryDao.insertProduct(p.copy(id = 0L, businessId = targetBizId, categoryId = null, unitId = null))
                        count++
                        recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "PRODUCT", sourceIdentifier = p.name, mappedLocalId = newId, status = "IMPORTED")
                    }
                }
            }
        }
        return count
    }

    private suspend fun recoverFromSqliteBytes(
        bytes: ByteArray,
        targetBizId: Long,
        duplicateStrategy: DuplicateStrategy,
        recordsOut: MutableList<RecoveryRecord>
    ): Pair<Int, Int> {
        val tempDb = File.createTempFile("inspect_", ".db", context.cacheDir)
        var recovered = 0
        var unsupported = 0
        try {
            tempDb.writeBytes(bytes)
            val sq = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            sq.use { dbConn ->
                val tables = mutableListOf<String>()
                dbConn.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    while (c.moveToNext()) tables += c.getString(0)
                }
                val prodTable = tables.firstOrNull { it.contains("product", true) || it.contains("item", true) }
                if (prodTable != null) {
                    dbConn.rawQuery("SELECT * FROM \"$prodTable\" LIMIT 500", null).use { c ->
                        val cols = c.columnNames.map { it.lowercase() }
                        val nameIdx = cols.indexOfFirst { it.contains("name") }
                        val priceIdx = cols.indexOfFirst { it.contains("selling") || it.contains("price") || it.contains("rate") }
                        if (nameIdx >= 0) {
                            while (c.moveToNext()) {
                                val pName = c.getString(nameIdx)?.trim().orEmpty()
                                if (pName.isBlank()) continue
                                val rawPrice = if (priceIdx >= 0) c.getLong(priceIdx) else 0L
                                val paise = if (rawPrice > 100_000L) rawPrice else rawPrice * 100L
                                val ex = inventoryDao.findProductByName(pName, targetBizId)
                                if (ex == null) {
                                    val nid = inventoryDao.insertProduct(Product(businessId = targetBizId, name = pName, sellingPricePaise = paise))
                                    recovered++
                                    recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "PRODUCT", sourceIdentifier = pName, mappedLocalId = nid, status = "IMPORTED")
                                } else if (duplicateStrategy == DuplicateStrategy.SKIP) {
                                    recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "PRODUCT", sourceIdentifier = pName, mappedLocalId = ex.id, status = "SKIPPED")
                                }
                            }
                        } else unsupported++
                    }
                }
                val partyTable = tables.firstOrNull { it.contains("customer", true) || it.contains("part", true) }
                if (partyTable != null) {
                    dbConn.rawQuery("SELECT * FROM \"$partyTable\" LIMIT 500", null).use { c ->
                        val cols = c.columnNames.map { it.lowercase() }
                        val nameIdx = cols.indexOfFirst { it.contains("name") }
                        val phoneIdx = cols.indexOfFirst { it.contains("phone") || it.contains("mobile") }
                        if (nameIdx >= 0) {
                            while (c.moveToNext()) {
                                val cName = c.getString(nameIdx)?.trim().orEmpty()
                                if (cName.isBlank()) continue
                                val phone = if (phoneIdx >= 0) c.getString(phoneIdx).orEmpty() else ""
                                val ex = partyDao.findCustomerByName(cName, targetBizId)
                                if (ex == null) {
                                    val nid = partyDao.insertCustomer(Customer(businessId = targetBizId, name = cName, phone = phone))
                                    recovered++
                                    recordsOut += RecoveryRecord(recoveryHistoryId = 0L, recordCategory = "CUSTOMER", sourceIdentifier = cName, mappedLocalId = nid, status = "IMPORTED")
                                }
                            }
                        } else unsupported++
                    }
                }
            }
        } finally {
            tempDb.delete()
        }
        return recovered to unsupported
    }

    private fun guessTargetTypeFromHeaders(headers: List<String>): ImportTargetType {
        val joined = headers.joinToString(" ").lowercase()
        return when {
            joined.contains("selling") || joined.contains("mrp") || joined.contains("hsn") || joined.contains("barcode") -> ImportTargetType.PRODUCTS
            joined.contains("supplier") -> ImportTargetType.SUPPLIERS
            joined.contains("customer") || joined.contains("phone") || joined.contains("gstin") -> ImportTargetType.CUSTOMERS
            else -> ImportTargetType.PRODUCTS
        }
    }
}
