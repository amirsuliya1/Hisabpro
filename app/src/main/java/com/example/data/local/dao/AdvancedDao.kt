package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.BackupHistory
import com.example.data.local.entities.BusinessHealthMetric
import com.example.data.local.entities.DailyClosing
import com.example.data.local.entities.DailyClosingEntry
import com.example.data.local.entities.ExpiryAlert
import com.example.data.local.entities.ImportHistory
import com.example.data.local.entities.ImportMapping
import com.example.data.local.entities.InvoiceAuditLog
import com.example.data.local.entities.LoyaltyAccount
import com.example.data.local.entities.LoyaltyConfiguration
import com.example.data.local.entities.LoyaltyTransaction
import com.example.data.local.entities.PriceHistory
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.local.entities.ProductBatch
import com.example.data.local.entities.RecoveryHistory
import com.example.data.local.entities.RecoveryRecord
import com.example.data.local.entities.ReminderHistory
import com.example.data.local.entities.ReorderSuggestion
import com.example.data.local.entities.SearchHistory
import com.example.data.local.entities.SearchModule
import com.example.data.local.entities.StockPrediction
import com.example.data.local.entities.UpiConfiguration
import com.example.data.local.entities.VoiceCommandHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvancedDao {
    // Voice Command History
    @Query("SELECT * FROM voice_command_history WHERE businessId = :businessId ORDER BY createdAt DESC LIMIT 100")
    fun observeVoiceHistory(businessId: Long): Flow<List<VoiceCommandHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceHistory(item: VoiceCommandHistory): Long

    // Stock Prediction & Reorder
    @Query("SELECT * FROM stock_predictions WHERE businessId = :businessId")
    fun observeStockPredictions(businessId: Long): Flow<List<StockPrediction>>

    @Query("SELECT * FROM stock_predictions WHERE businessId = :businessId")
    suspend fun getStockPredictionsOnce(businessId: Long): List<StockPrediction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStockPrediction(prediction: StockPrediction): Long

    @Query("SELECT * FROM reorder_suggestions WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun observeReorderSuggestions(businessId: Long): Flow<List<ReorderSuggestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReorderSuggestion(suggestion: ReorderSuggestion): Long

    // Reminder History
    @Query("SELECT * FROM reminder_history WHERE businessId = :businessId ORDER BY sentAtMillis DESC LIMIT 200")
    fun observeReminderHistory(businessId: Long): Flow<List<ReminderHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminderHistory(history: ReminderHistory): Long

    // Printer Configurations
    @Query("SELECT * FROM printer_configurations WHERE businessId = :businessId ORDER BY isDefault DESC, updatedAt DESC")
    fun observePrinterConfigs(businessId: Long): Flow<List<PrinterConfiguration>>

    @Query("SELECT * FROM printer_configurations WHERE businessId = :businessId ORDER BY isDefault DESC, updatedAt DESC LIMIT 1")
    suspend fun getDefaultPrinterConfigOnce(businessId: Long): PrinterConfiguration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinterConfig(config: PrinterConfiguration): Long

    @Update
    suspend fun updatePrinterConfig(config: PrinterConfiguration)

    @Delete
    suspend fun deletePrinterConfig(config: PrinterConfiguration)

    // UPI Configurations
    @Query("SELECT * FROM upi_configurations WHERE businessId = :businessId ORDER BY isDefault DESC, updatedAt DESC")
    fun observeUpiConfigs(businessId: Long): Flow<List<UpiConfiguration>>

    @Query("SELECT * FROM upi_configurations WHERE businessId = :businessId ORDER BY isDefault DESC LIMIT 1")
    suspend fun getDefaultUpiConfigOnce(businessId: Long): UpiConfiguration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpiConfig(config: UpiConfiguration): Long

    @Delete
    suspend fun deleteUpiConfig(config: UpiConfiguration)

    // Loyalty Program
    @Query("SELECT * FROM loyalty_configurations WHERE businessId = :businessId LIMIT 1")
    fun observeLoyaltyConfig(businessId: Long): Flow<LoyaltyConfiguration?>

    @Query("SELECT * FROM loyalty_configurations WHERE businessId = :businessId LIMIT 1")
    suspend fun getLoyaltyConfigOnce(businessId: Long): LoyaltyConfiguration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoyaltyConfig(config: LoyaltyConfiguration): Long

    @Query("SELECT * FROM loyalty_accounts WHERE businessId = :businessId ORDER BY pointsBalance DESC")
    fun observeLoyaltyAccounts(businessId: Long): Flow<List<LoyaltyAccount>>

    @Query("SELECT * FROM loyalty_accounts WHERE businessId = :businessId AND customerId = :customerId LIMIT 1")
    suspend fun getLoyaltyAccountOnce(businessId: Long, customerId: Long): LoyaltyAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoyaltyAccount(account: LoyaltyAccount): Long

    @Query("SELECT * FROM loyalty_transactions WHERE businessId = :businessId ORDER BY createdAt DESC LIMIT 200")
    fun observeLoyaltyTransactions(businessId: Long): Flow<List<LoyaltyTransaction>>

    @Query("SELECT COUNT(*) FROM loyalty_transactions WHERE invoiceId = :invoiceId AND txnType = :txnType")
    suspend fun countLoyaltyTxnForInvoice(invoiceId: Long, txnType: com.example.data.local.entities.LoyaltyTxnType): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoyaltyTransaction(txn: LoyaltyTransaction): Long

    // Business Health Metrics
    @Query("SELECT * FROM business_health_metrics WHERE businessId = :businessId ORDER BY recordedAtMillis DESC LIMIT 30")
    fun observeBusinessHealthMetrics(businessId: Long): Flow<List<BusinessHealthMetric>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusinessHealthMetric(metric: BusinessHealthMetric): Long

    // Price History
    @Query("SELECT * FROM price_history WHERE businessId = :businessId ORDER BY changedAtMillis DESC LIMIT 300")
    fun observeAllPriceHistory(businessId: Long): Flow<List<PriceHistory>>

    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY changedAtMillis DESC")
    fun observePriceHistoryForProduct(productId: Long): Flow<List<PriceHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: PriceHistory): Long

    // Product Batches & Expiry Alerts
    @Query("SELECT * FROM product_batches WHERE businessId = :businessId AND isActive = 1 ORDER BY expiryDateMillis ASC")
    fun observeAllBatches(businessId: Long): Flow<List<ProductBatch>>

    @Query("SELECT * FROM product_batches WHERE productId = :productId AND isActive = 1 AND remainingQuantityScaled > 0 ORDER BY expiryDateMillis ASC")
    fun observeAvailableBatchesForProduct(productId: Long): Flow<List<ProductBatch>>

    @Query("SELECT * FROM product_batches WHERE id = :id LIMIT 1")
    suspend fun getBatchByIdOnce(id: Long): ProductBatch?

    @Query("SELECT * FROM product_batches WHERE productId = :productId AND batchNumber = :batchNumber LIMIT 1")
    suspend fun findBatchByProductAndNumber(productId: Long, batchNumber: String): ProductBatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: ProductBatch): Long

    @Update
    suspend fun updateBatch(batch: ProductBatch)

    @Query("UPDATE product_batches SET remainingQuantityScaled = remainingQuantityScaled + :deltaScaled WHERE id = :batchId")
    suspend fun adjustBatchQuantity(batchId: Long, deltaScaled: Long)

    @Query("SELECT * FROM expiry_alerts WHERE businessId = :businessId ORDER BY expiryDateMillis ASC")
    fun observeExpiryAlerts(businessId: Long): Flow<List<ExpiryAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpiryAlert(alert: ExpiryAlert): Long

    // Daily Closings
    @Query("SELECT * FROM daily_closings WHERE businessId = :businessId ORDER BY closingDateIso DESC, id DESC")
    fun observeDailyClosings(businessId: Long): Flow<List<DailyClosing>>

    @Query("SELECT * FROM daily_closings WHERE businessId = :businessId AND closingDateIso = :dateIso LIMIT 1")
    suspend fun getDailyClosingForDate(businessId: Long, dateIso: String): DailyClosing?

    @Query("SELECT * FROM daily_closings WHERE businessId = :businessId ORDER BY closingDateIso DESC, id DESC LIMIT 1")
    suspend fun getLatestDailyClosing(businessId: Long): DailyClosing?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDailyClosing(closing: DailyClosing): Long

    @Query("SELECT * FROM daily_closing_entries WHERE dailyClosingId = :closingId")
    fun observeClosingEntries(closingId: Long): Flow<List<DailyClosingEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosingEntries(entries: List<DailyClosingEntry>)

    // Backup, Import & Recovery History
    @Query("SELECT * FROM backup_history WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun observeBackupHistory(businessId: Long): Flow<List<BackupHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupHistory(history: BackupHistory): Long

    @Query("SELECT * FROM import_history WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun observeImportHistory(businessId: Long): Flow<List<ImportHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportHistory(history: ImportHistory): Long

    @Update
    suspend fun updateImportHistory(history: ImportHistory)

    @Query("SELECT * FROM recovery_history WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun observeRecoveryHistory(businessId: Long): Flow<List<RecoveryHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecoveryHistory(history: RecoveryHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecoveryRecords(records: List<RecoveryRecord>)

    @Query("SELECT * FROM recovery_records WHERE recoveryHistoryId = :recoveryId")
    fun observeRecoveryRecords(recoveryId: Long): Flow<List<RecoveryRecord>>

    @Query("SELECT * FROM import_mappings WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun observeImportMappings(businessId: Long): Flow<List<ImportMapping>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportMapping(mapping: ImportMapping): Long

    // Invoice Audit Logs
    @Query("SELECT * FROM invoice_audit_logs WHERE invoiceId = :invoiceId ORDER BY createdAt DESC")
    fun observeInvoiceAuditLogs(invoiceId: Long): Flow<List<InvoiceAuditLog>>

    @Query("SELECT * FROM invoice_audit_logs WHERE businessId = :businessId ORDER BY createdAt DESC LIMIT 200")
    fun observeBusinessInvoiceAuditLogs(businessId: Long): Flow<List<InvoiceAuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceAuditLog(log: InvoiceAuditLog): Long

    // Local Search History (Inventory & Invoice Modules)
    @Query("SELECT * FROM search_history WHERE businessId = :businessId AND module = :module ORDER BY searchedAtMillis DESC LIMIT :limit")
    fun observeRecentSearches(businessId: Long, module: SearchModule, limit: Int = 10): Flow<List<SearchHistory>>

    @Query("SELECT * FROM search_history WHERE businessId = :businessId AND module = :module ORDER BY searchedAtMillis DESC LIMIT :limit")
    suspend fun getRecentSearchesOnce(businessId: Long, module: SearchModule, limit: Int = 10): List<SearchHistory>

    @Query("SELECT * FROM search_history WHERE businessId = :businessId ORDER BY searchedAtMillis DESC")
    suspend fun getAllSearchHistoryOnce(businessId: Long): List<SearchHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistory): Long

    @Query("DELETE FROM search_history WHERE businessId = :businessId AND module = :module AND LOWER(query) = LOWER(:query)")
    suspend fun deleteSearchByQueryIgnoreCase(businessId: Long, module: SearchModule, query: String)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistoryItem(id: Long)

    @Query("DELETE FROM search_history WHERE businessId = :businessId AND module = :module")
    suspend fun clearSearchHistoryForModule(businessId: Long, module: SearchModule)

    @Query("DELETE FROM search_history WHERE businessId = :businessId AND module = :module AND id NOT IN (SELECT id FROM search_history WHERE businessId = :businessId AND module = :module ORDER BY searchedAtMillis DESC LIMIT :maxCount)")
    suspend fun trimSearchHistory(businessId: Long, module: SearchModule, maxCount: Int = 10)
}
