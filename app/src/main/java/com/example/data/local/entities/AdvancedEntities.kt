package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "voice_command_history",
    indices = [Index("businessId"), Index("createdAt")]
)
data class VoiceCommandHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val recognizedText: String,
    val parsedIntent: String,
    val actionSummary: String,
    val wasOnlineRecognizer: Boolean = false,
    val confirmedAndExecuted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "stock_predictions",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index(value = ["businessId", "productId"], unique = true)]
)
data class StockPrediction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val productId: Long,
    val avgDailySalesScaled: Long = 0L,
    val estimatedDaysRemaining: Int = -1,
    val suggestedReorderScaled: Long = 0L,
    val manualReorderOverrideScaled: Long? = null,
    val velocityClass: String = "INSUFFICIENT_DATA", // FAST, NORMAL, SLOW, OVERSTOCK, INSUFFICIENT_DATA
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "reorder_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("productId")]
)
data class ReorderSuggestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val productId: Long,
    val productName: String,
    val suggestedQtyScaled: Long,
    val customQtyScaled: Long,
    val status: String = "PENDING", // PENDING, ORDERED, DISMISSED
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "reminder_history",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("customerId"), Index("sentAtMillis")]
)
data class ReminderHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val customerId: Long,
    val customerName: String,
    val invoiceId: Long? = null,
    val invoiceNumber: String = "",
    val amountDuePaise: Long,
    val channel: ReminderChannel,
    val languageCode: String = "en",
    val messageText: String,
    val deliveryNote: String = "Shared via Android Intent / Marked Manually",
    val sentAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "printer_configurations",
    indices = [Index("businessId")]
)
data class PrinterConfiguration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val printerName: String = "Thermal Printer",
    val macAddress: String = "",
    val paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58,
    val showLogo: Boolean = true,
    val showBusinessName: Boolean = true,
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val showGstin: Boolean = true,
    val showInvoiceNumber: Boolean = true,
    val showDateTime: Boolean = true,
    val showItemTable: Boolean = true,
    val showDiscount: Boolean = true,
    val showTax: Boolean = true,
    val showPaidAndDue: Boolean = true,
    val showPaymentQr: Boolean = true,
    val footerMessage: String = "Thank you! Visit again.",
    val isDefault: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "upi_configurations",
    indices = [Index("businessId")]
)
data class UpiConfiguration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val upiId: String,
    val payeeName: String,
    val paymentInstructions: String = "Scan via any UPI app and share reference ID with cashier.",
    val showOnInvoices: Boolean = true,
    val showOnReceipts: Boolean = true,
    val isDefault: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "loyalty_configurations",
    indices = [Index(value = ["businessId"], unique = true)]
)
data class LoyaltyConfiguration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val isEnabled: Boolean = true,
    val rupeesPerPoint: Long = 100L, // ₹100 spend = 1 point
    val pointValuePaise: Long = 100L, // 1 point = ₹1.00 discount
    val minRedeemPoints: Long = 50L,
    val maxRedeemPointsPerInvoice: Long = 500L,
    val minInvoiceAmountPaise: Long = 100_00L,
    val pointsExpiryDays: Int = 365,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "loyalty_accounts",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index(value = ["businessId", "customerId"], unique = true)]
)
data class LoyaltyAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val customerId: Long,
    val pointsBalance: Long = 0L,
    val lifetimePointsEarned: Long = 0L,
    val lifetimePointsRedeemed: Long = 0L,
    val membershipTier: LoyaltyTier = LoyaltyTier.SILVER,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "loyalty_transactions",
    foreignKeys = [
        ForeignKey(
            entity = LoyaltyAccount::class,
            parentColumns = ["id"],
            childColumns = ["loyaltyAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("businessId"),
        Index("loyaltyAccountId"),
        Index("invoiceId"),
        Index(value = ["invoiceId", "txnType"], unique = true)
    ]
)
data class LoyaltyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val loyaltyAccountId: Long,
    val customerId: Long,
    val invoiceId: Long? = null,
    val txnType: LoyaltyTxnType,
    val pointsDelta: Long, // positive for EARNED, negative for REDEEMED/EXPIRED
    val balanceAfter: Long,
    val notes: String = "",
    val expiresAtMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "business_health_metrics",
    indices = [Index("businessId"), Index("recordedAtMillis")]
)
data class BusinessHealthMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val overallScore: Int,
    val metricDetailsJson: String,
    val strengthsSummary: String,
    val weaknessesSummary: String,
    val recordedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "price_history",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("productId"), Index("changedAtMillis")]
)
data class PriceHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val productId: Long,
    val productName: String,
    val purchasePricePaise: Long,
    val sellingPricePaise: Long,
    val wholesalePricePaise: Long,
    val mrpPaise: Long,
    val discountPercentScaled: Long = 0L,
    val supplierName: String = "",
    val changeSource: String = "PRODUCT_EDIT", // PRODUCT_EDIT, PURCHASE, BULK_UPDATE
    val changedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "product_batches",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("businessId"), Index("productId"), Index("supplierId"), Index("expiryDateMillis")]
)
data class ProductBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val productId: Long,
    val supplierId: Long? = null,
    val supplierName: String = "",
    val batchNumber: String,
    val manufacturingDateMillis: Long? = null,
    val expiryDateMillis: Long? = null,
    val purchaseDateMillis: Long = System.currentTimeMillis(),
    val initialQuantityScaled: Long,
    val remainingQuantityScaled: Long,
    val unitCostPaise: Long = 0L,
    val purchaseId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "expiry_alerts",
    foreignKeys = [
        ForeignKey(
            entity = ProductBatch::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("batchId")]
)
data class ExpiryAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val batchId: Long,
    val productId: Long,
    val productName: String,
    val batchNumber: String,
    val expiryDateMillis: Long,
    val alertLevel: String, // EXPIRED, WITHIN_7D, WITHIN_30D, WITHIN_60D
    val acknowledged: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "daily_closings",
    indices = [Index("businessId"), Index(value = ["businessId", "closingDateIso"], unique = true)]
)
data class DailyClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val closingDateIso: String, // YYYY-MM-DD
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val openingCashPaise: Long,
    val cashSalesPaise: Long,
    val cashReceiptsPaise: Long,
    val cashExpensesPaise: Long,
    val cashPaymentsPaise: Long,
    val cashRefundsPaise: Long,
    val expectedClosingCashPaise: Long,
    val actualCountedCashPaise: Long,
    val cashDifferencePaise: Long, // actual - expected (+ excess, - shortage)
    val upiCollectionsPaise: Long,
    val bankCollectionsPaise: Long,
    val creditSalesPaise: Long,
    val totalSalesPaise: Long,
    val totalExpensesPaise: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "daily_closing_entries",
    foreignKeys = [
        ForeignKey(
            entity = DailyClosing::class,
            parentColumns = ["id"],
            childColumns = ["dailyClosingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("dailyClosingId")]
)
data class DailyClosingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dailyClosingId: Long,
    val entryType: String, // CASH_SALE, CASH_RECEIPT, CASH_EXPENSE, CASH_PAYMENT, UPI_COLLECTION, BANK_COLLECTION, CREDIT_SALE, SHORTAGE_EXCESS
    val label: String,
    val amountPaise: Long,
    val reference: String = ""
)

@Serializable
@Entity(
    tableName = "backup_history",
    indices = [Index("businessId"), Index("createdAt")]
)
data class BackupHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val fileName: String,
    val fileExtension: String, // bizmgr, hpbk, json
    val safUriString: String = "",
    val triggerType: String = "MANUAL", // MANUAL, DAILY_AUTO, WEEKLY_AUTO, PRE_RESTORE, PRE_IMPORT
    val isEncrypted: Boolean = true,
    val checksumSha256: String = "",
    val recordCount: Int = 0,
    val sizeBytes: Long = 0L,
    val verificationStatus: String = "VERIFIED",
    val restoreStatus: String = "NOT_RESTORED",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "import_history",
    indices = [Index("businessId"), Index("createdAt")]
)
data class ImportHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val sourceFileName: String,
    val detectedFormat: String,
    val targetEntityType: String, // PRODUCTS, CUSTOMERS, SUPPLIERS, OPENING_STOCK, MIXED
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP,
    val importedCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val errorCount: Int = 0,
    val safetyBackupChecksum: String = "",
    val createdRecordIdsJson: String = "", // For safe undo of newly inserted records
    val wasUndone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "recovery_history",
    indices = [Index("businessId"), Index("createdAt")]
)
data class RecoveryHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val sourceFileName: String,
    val detectedFileType: String,
    val recoveryMode: RecoveryMode,
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP,
    val safetyBackupFile: String = "",
    val recoveredCount: Int = 0,
    val unsupportedCount: Int = 0,
    val errorCount: Int = 0,
    val status: String = "COMPLETED", // COMPLETED, PARTIAL, FAILED, ROLLED_BACK
    val reportSummary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "recovery_records",
    foreignKeys = [
        ForeignKey(
            entity = RecoveryHistory::class,
            parentColumns = ["id"],
            childColumns = ["recoveryHistoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recoveryHistoryId")]
)
data class RecoveryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recoveryHistoryId: Long,
    val recordCategory: String, // PRODUCT, CUSTOMER, SUPPLIER, INVOICE, EXPENSE, UNSUPPORTED
    val sourceIdentifier: String = "",
    val mappedLocalId: Long? = null,
    val status: String, // IMPORTED, UPDATED, SKIPPED, UNSUPPORTED, ERROR
    val message: String = ""
)

@Serializable
@Entity(
    tableName = "import_mappings",
    indices = [Index("businessId")]
)
data class ImportMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val targetType: String, // PRODUCTS, CUSTOMERS, SUPPLIERS, OPENING_STOCK
    val columnMapJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "invoice_audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("invoiceId"), Index("createdAt")]
)
data class InvoiceAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val invoiceId: Long,
    val invoiceNumber: String,
    val action: String, // CREATED, DRAFT_EDITED, FINALIZED, PAYMENT_ADDED, CANCELLED_REVERSED, CREDIT_NOTE, THERMAL_PRINTED
    val details: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "search_history",
    indices = [
        Index("businessId"),
        Index(value = ["businessId", "module", "searchedAtMillis"]),
        Index(value = ["businessId", "module", "query"], unique = true)
    ]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val module: SearchModule,
    val query: String,
    val searchedAtMillis: Long = System.currentTimeMillis()
)
