package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.example.core.SecurityManager
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.Account
import com.example.data.local.entities.AppSettings
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.BackupHistory
import com.example.data.local.entities.BackupMetadata
import com.example.data.local.entities.BusinessHealthMetric
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Customer
import com.example.data.local.entities.DailyClosing
import com.example.data.local.entities.DailyClosingEntry
import com.example.data.local.entities.Expense
import com.example.data.local.entities.ExpenseCategory
import com.example.data.local.entities.ExpiryAlert
import com.example.data.local.entities.ImportHistory
import com.example.data.local.entities.ImportMapping
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceAuditLog
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceTemplate
import com.example.data.local.entities.JournalEntry
import com.example.data.local.entities.LedgerEntry
import com.example.data.local.entities.LoyaltyAccount
import com.example.data.local.entities.LoyaltyConfiguration
import com.example.data.local.entities.LoyaltyTransaction
import com.example.data.local.entities.Payment
import com.example.data.local.entities.PaymentAllocation
import com.example.data.local.entities.PriceHistory
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.local.entities.Product
import com.example.data.local.entities.ProductBatch
import com.example.data.local.entities.ProductCategory
import com.example.data.local.entities.Purchase
import com.example.data.local.entities.PurchaseItem
import com.example.data.local.entities.ReminderHistory
import com.example.data.local.entities.ReorderSuggestion
import com.example.data.local.entities.SearchHistory
import com.example.data.local.entities.StockMovement
import com.example.data.local.entities.StockPrediction
import com.example.data.local.entities.Supplier
import com.example.data.local.entities.TaxConfiguration
import com.example.data.local.entities.UnitEntity
import com.example.data.local.entities.UpiConfiguration
import com.example.data.local.entities.VoiceCommandHistory
import com.example.data.local.entities.Warehouse
import com.example.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class FullBackupSnapshot(
    val schemaVersion: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val checksumSha256: String = "",
    val businessProfile: BusinessProfile? = null,
    val businessProfiles: List<BusinessProfile> = emptyList(),
    val appSettings: AppSettings? = null,
    val taxConfigurations: List<TaxConfiguration> = emptyList(),
    val invoiceTemplates: List<InvoiceTemplate> = emptyList(),
    val productCategories: List<ProductCategory> = emptyList(),
    val units: List<UnitEntity> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val products: List<Product> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val invoiceItems: List<InvoiceItem> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val purchaseItems: List<PurchaseItem> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val paymentAllocations: List<PaymentAllocation> = emptyList(),
    val expenseCategories: List<ExpenseCategory> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val ledgerEntries: List<LedgerEntry> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    // v3 enterprise tables
    val priceHistories: List<PriceHistory> = emptyList(),
    val productBatches: List<ProductBatch> = emptyList(),
    val expiryAlerts: List<ExpiryAlert> = emptyList(),
    val stockPredictions: List<StockPrediction> = emptyList(),
    val reorderSuggestions: List<ReorderSuggestion> = emptyList(),
    val reminderHistories: List<ReminderHistory> = emptyList(),
    val printerConfigurations: List<PrinterConfiguration> = emptyList(),
    val upiConfigurations: List<UpiConfiguration> = emptyList(),
    val loyaltyConfigurations: List<LoyaltyConfiguration> = emptyList(),
    val loyaltyAccounts: List<LoyaltyAccount> = emptyList(),
    val loyaltyTransactions: List<LoyaltyTransaction> = emptyList(),
    val businessHealthMetrics: List<BusinessHealthMetric> = emptyList(),
    val dailyClosings: List<DailyClosing> = emptyList(),
    val dailyClosingEntries: List<DailyClosingEntry> = emptyList(),
    val voiceCommandHistories: List<VoiceCommandHistory> = emptyList(),
    val invoiceAuditLogs: List<InvoiceAuditLog> = emptyList(),
    val importMappings: List<ImportMapping> = emptyList(),
    val importHistories: List<ImportHistory> = emptyList(),
    val searchHistories: List<SearchHistory> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepository(
    private val context: Context,
    private val db: HisabPulseDatabase,
    private val prefs: AppPreferencesRepository
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val activeBizFlow: Flow<Long> = db.coreDao().observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val backupHistory: Flow<List<BackupMetadata>> = activeBizFlow.flatMapLatest { db.coreDao().observeBackupMetadata(it) }
    val detailedBackupHistory: Flow<List<BackupHistory>> = activeBizFlow.flatMapLatest { db.advancedDao().observeBackupHistory(it) }

    suspend fun exportBackupToUri(
        uri: Uri,
        fileName: String,
        encrypt: Boolean,
        passphrase: String,
        triggerType: String = "MANUAL"
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val snap = createSnapshot()
            val bytes = encodeSnapshot(snap, encrypt, passphrase)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Unable to open backup destination")

            val bizId = snap.appSettings?.activeBusinessId ?: 1L
            val count = snap.customers.size + snap.suppliers.size + snap.products.size +
                snap.invoices.size + snap.purchases.size + snap.payments.size + snap.expenses.size
            val checksum = SecurityManager.sha256Hex(bytes)
            val ext = fileName.substringAfterLast('.', if (encrypt) "bizmgr" else "json").lowercase()

            val meta = BackupMetadata(
                businessId = bizId,
                fileName = fileName,
                safUriString = uri.toString(),
                schemaVersion = snap.schemaVersion,
                isEncrypted = encrypt,
                checksumSha256 = checksum,
                recordCount = count,
                sizeBytes = bytes.size.toLong(),
                verificationStatus = "VERIFIED",
                restoreStatus = "READY"
            )
            db.coreDao().insertBackupMetadata(meta)
            db.advancedDao().insertBackupHistory(
                BackupHistory(
                    businessId = bizId,
                    fileName = fileName,
                    fileExtension = ext,
                    safUriString = uri.toString(),
                    triggerType = triggerType,
                    isEncrypted = encrypt,
                    checksumSha256 = checksum,
                    recordCount = count,
                    sizeBytes = bytes.size.toLong()
                )
            )
            db.coreDao().insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "CREATE_BACKUP",
                    entityType = "Backup",
                    entityId = 0L,
                    summary = "Exported ${if (encrypt) "encrypted .$ext" else "JSON"} backup ($count records)"
                )
            )
            meta
        }
    }

    suspend fun exportAutoBackupToTreeUri(treeUri: Uri, fileName: String, triggerType: String): Result<BackupMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                val docUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    "application/octet-stream",
                    fileName
                ) ?: error("Could not create scheduled backup file in selected folder")
                exportBackupToUri(
                    uri = docUri,
                    fileName = fileName,
                    encrypt = true,
                    passphrase = "",
                    triggerType = triggerType
                ).getOrThrow()
            }
        }

    suspend fun createSafetySnapshotFile(prefix: String = "pre_restore_safety"): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "safety_backups").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.bizmgr")
        val bytes = createBackupBytes(encrypt = true, passphrase = "")
        file.writeBytes(bytes)
        val bizId = db.coreDao().getAppSettingsOnce()?.activeBusinessId ?: 1L
        db.advancedDao().insertBackupHistory(
            BackupHistory(
                businessId = bizId,
                fileName = file.name,
                fileExtension = "bizmgr",
                safUriString = Uri.fromFile(file).toString(),
                triggerType = "PRE_RESTORE",
                isEncrypted = true,
                checksumSha256 = SecurityManager.sha256Hex(bytes),
                sizeBytes = bytes.size.toLong()
            )
        )
        file
    }

    suspend fun createBackupBytes(encrypt: Boolean, passphrase: String): ByteArray {
        val rawSnapshot = createSnapshot()
        return encodeSnapshot(rawSnapshot, encrypt, passphrase)
    }

    private fun encodeSnapshot(rawSnapshot: FullBackupSnapshot, encrypt: Boolean, passphrase: String): ByteArray {
        val payloadWithoutChecksum = json.encodeToString(FullBackupSnapshot.serializer(), rawSnapshot.copy(checksumSha256 = ""))
        val checksum = SecurityManager.sha256Hex(payloadWithoutChecksum.toByteArray(Charsets.UTF_8))
        val signedSnapshot = rawSnapshot.copy(checksumSha256 = checksum)
        val jsonBytes = json.encodeToString(FullBackupSnapshot.serializer(), signedSnapshot).toByteArray(Charsets.UTF_8)
        return if (encrypt) SecurityManager.encryptAesGcm(jsonBytes, passphrase) else jsonBytes
    }

    suspend fun inspectBackupFromUri(uri: Uri, passphrase: String): Result<FullBackupSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected backup file")
                decodeAndValidateBackupBytes(bytes, passphrase)
            }
        }

    fun decodeAndValidateBackupBytes(bytes: ByteArray, passphrase: String): FullBackupSnapshot {
        val plainBytes = if (SecurityManager.isEncryptedBackup(bytes)) {
            SecurityManager.decryptAesGcm(bytes, passphrase)
        } else {
            bytes
        }
        val text = String(plainBytes, Charsets.UTF_8)
        val snapshot = json.decodeFromString(FullBackupSnapshot.serializer(), text)
        require(snapshot.schemaVersion in 1..5) {
            "Unsupported backup version: ${snapshot.schemaVersion}"
        }
        if (snapshot.checksumSha256.isNotBlank()) {
            val baseJson = json.encodeToString(FullBackupSnapshot.serializer(), snapshot.copy(checksumSha256 = ""))
            val actualChecksum = SecurityManager.sha256Hex(baseJson.toByteArray(Charsets.UTF_8))
            require(actualChecksum == snapshot.checksumSha256) {
                "Backup integrity check failed (checksum mismatch)"
            }
        }
        return snapshot
    }

    suspend fun restoreFromSnapshot(snapshot: FullBackupSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.withTransaction {
                db.clearAllTables()

                val core = db.coreDao()
                val party = db.partyDao()
                val inv = db.inventoryDao()
                val bill = db.billingDao()
                val acc = db.accountingDao()
                val adv = db.advancedDao()

                snapshot.appSettings?.let { core.upsertAppSettings(it) }
                val profilesToRestore = if (snapshot.businessProfiles.isNotEmpty()) {
                    snapshot.businessProfiles
                } else {
                    listOfNotNull(snapshot.businessProfile)
                }
                profilesToRestore.forEach { core.upsertBusinessProfile(it) }

                snapshot.taxConfigurations.forEach { core.insertTaxConfiguration(it) }
                snapshot.invoiceTemplates.forEach { core.insertInvoiceTemplate(it) }
                snapshot.productCategories.forEach { inv.insertCategory(it) }
                snapshot.units.forEach { inv.insertUnit(it) }
                snapshot.warehouses.forEach { inv.insertWarehouse(it) }
                snapshot.customers.forEach { party.insertCustomer(it) }
                snapshot.suppliers.forEach { party.insertSupplier(it) }
                snapshot.products.forEach { inv.insertProduct(it) }
                snapshot.stockMovements.forEach { inv.insertStockMovement(it) }
                snapshot.invoices.forEach { bill.insertInvoice(it) }
                if (snapshot.invoiceItems.isNotEmpty()) bill.insertInvoiceItems(snapshot.invoiceItems)
                snapshot.purchases.forEach { bill.insertPurchase(it) }
                if (snapshot.purchaseItems.isNotEmpty()) bill.insertPurchaseItems(snapshot.purchaseItems)
                snapshot.accounts.forEach { acc.insertAccount(it) }
                snapshot.payments.forEach { acc.insertPayment(it) }
                snapshot.paymentAllocations.forEach { acc.insertPaymentAllocation(it) }
                snapshot.expenseCategories.forEach { acc.insertExpenseCategory(it) }
                snapshot.expenses.forEach { acc.insertExpense(it) }
                snapshot.journalEntries.forEach { acc.insertJournalEntry(it) }
                if (snapshot.ledgerEntries.isNotEmpty()) acc.insertLedgerEntries(snapshot.ledgerEntries)
                snapshot.auditLogs.forEach { core.insertAuditLog(it) }

                // v3 tables
                snapshot.priceHistories.forEach { adv.insertPriceHistory(it) }
                snapshot.productBatches.forEach { adv.insertBatch(it) }
                snapshot.expiryAlerts.forEach { adv.insertExpiryAlert(it) }
                snapshot.stockPredictions.forEach { adv.upsertStockPrediction(it) }
                snapshot.reorderSuggestions.forEach { adv.insertReorderSuggestion(it) }
                snapshot.reminderHistories.forEach { adv.insertReminderHistory(it) }
                snapshot.printerConfigurations.forEach { adv.insertPrinterConfig(it) }
                snapshot.upiConfigurations.forEach { adv.insertUpiConfig(it) }
                snapshot.loyaltyConfigurations.forEach { adv.upsertLoyaltyConfig(it) }
                snapshot.loyaltyAccounts.forEach { adv.upsertLoyaltyAccount(it) }
                snapshot.loyaltyTransactions.forEach { adv.insertLoyaltyTransaction(it) }
                snapshot.businessHealthMetrics.forEach { adv.insertBusinessHealthMetric(it) }
                snapshot.dailyClosings.forEach { adv.insertDailyClosing(it) }
                if (snapshot.dailyClosingEntries.isNotEmpty()) adv.insertClosingEntries(snapshot.dailyClosingEntries)
                snapshot.voiceCommandHistories.forEach { adv.insertVoiceHistory(it) }
                snapshot.invoiceAuditLogs.forEach { adv.insertInvoiceAuditLog(it) }
                snapshot.importMappings.forEach { adv.insertImportMapping(it) }
                snapshot.importHistories.forEach { adv.insertImportHistory(it) }
                snapshot.searchHistories.forEach { adv.upsertSearchHistory(it) }

                val activeId = snapshot.appSettings?.activeBusinessId ?: profilesToRestore.firstOrNull()?.id ?: 1L
                core.insertAuditLog(
                    AuditLog(
                        businessId = activeId,
                        action = "RESTORE_BACKUP",
                        entityType = "Backup",
                        entityId = 0L,
                        summary = "Restored snapshot v${snapshot.schemaVersion} (${snapshot.invoices.size} invoices, ${snapshot.products.size} products)"
                    )
                )
            }
            val restoredIds = db.coreDao().getAllBusinessProfilesOnce().map { it.id }.ifEmpty { listOf(1L) }
            restoredIds.forEach { HisabPulseDatabase.ensureBusinessSeeded(db, it) }
            snapshot.appSettings?.let {
                prefs.setThemeMode(it.themeMode)
                prefs.setOnboardingCompleted(it.onboardingCompleted)
                prefs.setHideAmounts(it.hideAmounts)
            }
            Unit
        }
    }

    suspend fun createSnapshot(): FullBackupSnapshot {
        val core = db.coreDao()
        val party = db.partyDao()
        val inv = db.inventoryDao()
        val bill = db.billingDao()
        val acc = db.accountingDao()
        val adv = db.advancedDao()

        val profiles = core.getAllBusinessProfilesOnce()
        val bizIds = profiles.map { it.id }.ifEmpty { listOf(1L) }

        val taxConfigs = bizIds.flatMap { core.getAllTaxConfigurationsOnce(it) }
        val templates = bizIds.flatMap { core.getAllInvoiceTemplatesOnce(it) }
        val categories = bizIds.flatMap { inv.getAllCategoriesOnce(it) }
        val units = bizIds.flatMap { inv.getAllUnitsOnce(it) }
        val warehouses = bizIds.flatMap { inv.getAllWarehousesOnce(it) }
        val customers = bizIds.flatMap { party.getAllCustomersOnce(it) }
        val suppliers = bizIds.flatMap { party.getAllSuppliersOnce(it) }
        val products = bizIds.flatMap { inv.getAllProductsOnce(it) }
        val movements = bizIds.flatMap { inv.getAllMovementsOnce(it) }
        val invoices = bizIds.flatMap { bill.getAllInvoicesOnce(it) }
        val invoiceItems = bizIds.flatMap { bill.getAllInvoiceItemsOnce(it) }
        val purchases = bizIds.flatMap { bill.getAllPurchasesOnce(it) }
        val purchaseItems = bizIds.flatMap { bill.getAllPurchaseItemsOnce(it) }
        val accounts = bizIds.flatMap { acc.getAllAccountsOnce(it) }
        val payments = bizIds.flatMap { acc.getAllPaymentsOnce(it) }
        val paymentAllocs = bizIds.flatMap { acc.getAllPaymentAllocationsOnce(it) }
        val expCats = bizIds.flatMap { acc.getAllExpenseCategoriesOnce(it) }
        val expenses = bizIds.flatMap { acc.getAllExpensesOnce(it) }
        val journals = bizIds.flatMap { acc.getAllJournalEntriesOnce(it) }
        val ledgers = bizIds.flatMap { acc.getAllLedgerEntriesOnce(it) }
        val audits = bizIds.flatMap { core.getAllAuditLogsOnce(it) }

        val loyaltyConfigs = bizIds.mapNotNull { adv.getLoyaltyConfigOnce(it) }
        val searchHistories = bizIds.flatMap { adv.getAllSearchHistoryOnce(it) }
        return FullBackupSnapshot(
            schemaVersion = 5,
            businessProfile = core.getBusinessProfileOnce(),
            businessProfiles = profiles,
            appSettings = core.getAppSettingsOnce(),
            taxConfigurations = taxConfigs,
            invoiceTemplates = templates,
            productCategories = categories,
            units = units,
            warehouses = warehouses,
            customers = customers,
            suppliers = suppliers,
            products = products,
            stockMovements = movements,
            invoices = invoices,
            invoiceItems = invoiceItems,
            purchases = purchases,
            purchaseItems = purchaseItems,
            accounts = accounts,
            payments = payments,
            paymentAllocations = paymentAllocs,
            expenseCategories = expCats,
            expenses = expenses,
            journalEntries = journals,
            ledgerEntries = ledgers,
            auditLogs = audits,
            loyaltyConfigurations = loyaltyConfigs,
            searchHistories = searchHistories
        )
    }
}
