package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AccountingDao
import com.example.data.local.dao.AdvancedDao
import com.example.data.local.dao.BillingDao
import com.example.data.local.dao.CoreDao
import com.example.data.local.dao.InventoryDao
import com.example.data.local.dao.PartyDao
import com.example.data.local.entities.Account
import com.example.data.local.entities.AccountType
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
import com.example.data.local.entities.InvoicePaperSize
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
import com.example.data.local.entities.RecoveryHistory
import com.example.data.local.entities.RecoveryRecord
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        BusinessProfile::class,
        Customer::class,
        Supplier::class,
        Product::class,
        ProductCategory::class,
        UnitEntity::class,
        Warehouse::class,
        StockMovement::class,
        Invoice::class,
        InvoiceItem::class,
        Purchase::class,
        PurchaseItem::class,
        Payment::class,
        PaymentAllocation::class,
        Expense::class,
        ExpenseCategory::class,
        Account::class,
        LedgerEntry::class,
        JournalEntry::class,
        TaxConfiguration::class,
        InvoiceTemplate::class,
        AuditLog::class,
        AppSettings::class,
        BackupMetadata::class,
        VoiceCommandHistory::class,
        StockPrediction::class,
        ReorderSuggestion::class,
        ReminderHistory::class,
        PrinterConfiguration::class,
        UpiConfiguration::class,
        LoyaltyConfiguration::class,
        LoyaltyAccount::class,
        LoyaltyTransaction::class,
        BusinessHealthMetric::class,
        PriceHistory::class,
        ProductBatch::class,
        ExpiryAlert::class,
        DailyClosing::class,
        DailyClosingEntry::class,
        BackupHistory::class,
        ImportHistory::class,
        RecoveryHistory::class,
        RecoveryRecord::class,
        ImportMapping::class,
        InvoiceAuditLog::class,
        SearchHistory::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(HisabPulseConverters::class)
abstract class HisabPulseDatabase : RoomDatabase() {
    abstract fun coreDao(): CoreDao
    abstract fun partyDao(): PartyDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun billingDao(): BillingDao
    abstract fun accountingDao(): AccountingDao
    abstract fun advancedDao(): AdvancedDao

    companion object {
        const val DATABASE_NAME = "hisabpulse_business.db"

        object AccountCodes {
            const val CASH = "1001"
            const val BANK = "1002"
            const val RECEIVABLES = "1100"
            const val INVENTORY = "1200"
            const val INPUT_GST = "1300"
            const val PAYABLES = "2001"
            const val OUTPUT_GST = "2100"
            const val CUSTOMER_ADVANCES = "2200"
            const val OWNER_EQUITY = "3001"
            const val SALES_REVENUE = "4001"
            const val OTHER_INCOME = "4002"
            const val COGS = "5001"
            const val PURCHASES = "5002"
            const val OPERATING_EXPENSES = "5100"
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_createdAt ON audit_logs(createdAt)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Multi-business & extended columns on existing tables
                db.execSQL("ALTER TABLE tax_configurations ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tax_configurations_businessId ON tax_configurations(businessId)")

                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN logoPosition TEXT NOT NULL DEFAULT 'LEFT'")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN showBusinessName INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN showAddress INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN showTaxBreakdown INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN showDiscountColumn INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN showPaymentInfo INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN showCustomerNotes INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN paperSize TEXT NOT NULL DEFAULT 'A4'")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN accentColorHex TEXT NOT NULL DEFAULT '#EA580C'")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN headerText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN termsText TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_templates_businessId ON invoice_templates(businessId)")

                db.execSQL("ALTER TABLE app_settings ADD COLUMN activeBusinessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN warnExpiredBatchSale INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN blockExpiredBatchSale INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoBackupSchedule TEXT NOT NULL DEFAULT 'OFF'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoBackupFolderUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN lastAutoBackupMillis INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE backup_metadata ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE backup_metadata ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'VERIFIED'")
                db.execSQL("ALTER TABLE backup_metadata ADD COLUMN restoreStatus TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_backup_metadata_businessId ON backup_metadata(businessId)")

                db.execSQL("ALTER TABLE audit_logs ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_businessId ON audit_logs(businessId)")

                db.execSQL("ALTER TABLE customers ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_businessId ON customers(businessId)")

                db.execSQL("ALTER TABLE suppliers ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_businessId ON suppliers(businessId)")

                db.execSQL("DROP INDEX IF EXISTS index_product_categories_name")
                db.execSQL("ALTER TABLE product_categories ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_product_categories_businessId_name ON product_categories(businessId, name)")

                db.execSQL("DROP INDEX IF EXISTS index_units_shortName")
                db.execSQL("ALTER TABLE units ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_units_businessId_shortName ON units(businessId, shortName)")

                db.execSQL("DROP INDEX IF EXISTS index_warehouses_name")
                db.execSQL("ALTER TABLE warehouses ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_warehouses_businessId_name ON warehouses(businessId, name)")

                db.execSQL("ALTER TABLE products ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE products ADD COLUMN defaultDiscountPercentScaled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN trackExpiry INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_businessId ON products(businessId)")

                db.execSQL("ALTER TABLE stock_movements ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_businessId ON stock_movements(businessId)")

                db.execSQL("DROP INDEX IF EXISTS index_invoices_invoiceNumber")
                db.execSQL("ALTER TABLE invoices ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoices ADD COLUMN paymentReference TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN loyaltyPointsEarned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN loyaltyPointsRedeemed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN loyaltyDiscountPaise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_businessId ON invoices(businessId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_businessId_invoiceNumber ON invoices(businessId, invoiceNumber)")

                db.execSQL("ALTER TABLE invoice_items ADD COLUMN batchId INTEGER")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN expiryDateMillis INTEGER")

                db.execSQL("DROP INDEX IF EXISTS index_purchases_purchaseNumber")
                db.execSQL("ALTER TABLE purchases ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_businessId ON purchases(businessId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchases_businessId_purchaseNumber ON purchases(businessId, purchaseNumber)")

                db.execSQL("DROP INDEX IF EXISTS index_accounts_code")
                db.execSQL("ALTER TABLE accounts ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_businessId ON accounts(businessId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_businessId_code ON accounts(businessId, code)")

                db.execSQL("ALTER TABLE payments ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_businessId ON payments(businessId)")

                db.execSQL("DROP INDEX IF EXISTS index_expense_categories_name")
                db.execSQL("ALTER TABLE expense_categories ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expense_categories_businessId_name ON expense_categories(businessId, name)")

                db.execSQL("ALTER TABLE expenses ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_businessId ON expenses(businessId)")

                db.execSQL("ALTER TABLE journal_entries ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_businessId ON journal_entries(businessId)")

                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN businessId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entries_businessId ON ledger_entries(businessId)")

                // 21 New Enterprise Tables
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voice_command_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        recognizedText TEXT NOT NULL,
                        parsedIntent TEXT NOT NULL,
                        actionSummary TEXT NOT NULL,
                        wasOnlineRecognizer INTEGER NOT NULL,
                        confirmedAndExecuted INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voice_command_history_businessId ON voice_command_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voice_command_history_createdAt ON voice_command_history(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_predictions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        avgDailySalesScaled INTEGER NOT NULL,
                        estimatedDaysRemaining INTEGER NOT NULL,
                        suggestedReorderScaled INTEGER NOT NULL,
                        manualReorderOverrideScaled INTEGER,
                        velocityClass TEXT NOT NULL,
                        lastUpdatedMillis INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_predictions_businessId ON stock_predictions(businessId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_predictions_businessId_productId ON stock_predictions(businessId, productId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reorder_suggestions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        suggestedQtyScaled INTEGER NOT NULL,
                        customQtyScaled INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reorder_suggestions_businessId ON reorder_suggestions(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reorder_suggestions_productId ON reorder_suggestions(productId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminder_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        customerId INTEGER NOT NULL,
                        customerName TEXT NOT NULL,
                        invoiceId INTEGER,
                        invoiceNumber TEXT NOT NULL,
                        amountDuePaise INTEGER NOT NULL,
                        channel TEXT NOT NULL,
                        languageCode TEXT NOT NULL,
                        messageText TEXT NOT NULL,
                        deliveryNote TEXT NOT NULL,
                        sentAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_history_businessId ON reminder_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_history_customerId ON reminder_history(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_history_sentAtMillis ON reminder_history(sentAtMillis)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS printer_configurations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        printerName TEXT NOT NULL,
                        macAddress TEXT NOT NULL,
                        paperWidth TEXT NOT NULL,
                        showLogo INTEGER NOT NULL,
                        showBusinessName INTEGER NOT NULL,
                        showAddress INTEGER NOT NULL,
                        showPhone INTEGER NOT NULL,
                        showGstin INTEGER NOT NULL,
                        showInvoiceNumber INTEGER NOT NULL,
                        showDateTime INTEGER NOT NULL,
                        showItemTable INTEGER NOT NULL,
                        showDiscount INTEGER NOT NULL,
                        showTax INTEGER NOT NULL,
                        showPaidAndDue INTEGER NOT NULL,
                        showPaymentQr INTEGER NOT NULL,
                        footerMessage TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_printer_configurations_businessId ON printer_configurations(businessId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS upi_configurations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        upiId TEXT NOT NULL,
                        payeeName TEXT NOT NULL,
                        paymentInstructions TEXT NOT NULL,
                        showOnInvoices INTEGER NOT NULL,
                        showOnReceipts INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_upi_configurations_businessId ON upi_configurations(businessId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loyalty_configurations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        rupeesPerPoint INTEGER NOT NULL,
                        pointValuePaise INTEGER NOT NULL,
                        minRedeemPoints INTEGER NOT NULL,
                        maxRedeemPointsPerInvoice INTEGER NOT NULL,
                        minInvoiceAmountPaise INTEGER NOT NULL,
                        pointsExpiryDays INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_loyalty_configurations_businessId ON loyalty_configurations(businessId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loyalty_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        customerId INTEGER NOT NULL,
                        pointsBalance INTEGER NOT NULL,
                        lifetimePointsEarned INTEGER NOT NULL,
                        lifetimePointsRedeemed INTEGER NOT NULL,
                        membershipTier TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loyalty_accounts_businessId ON loyalty_accounts(businessId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_loyalty_accounts_businessId_customerId ON loyalty_accounts(businessId, customerId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loyalty_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        loyaltyAccountId INTEGER NOT NULL,
                        customerId INTEGER NOT NULL,
                        invoiceId INTEGER,
                        txnType TEXT NOT NULL,
                        pointsDelta INTEGER NOT NULL,
                        balanceAfter INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        expiresAtMillis INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(loyaltyAccountId) REFERENCES loyalty_accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loyalty_transactions_businessId ON loyalty_transactions(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loyalty_transactions_loyaltyAccountId ON loyalty_transactions(loyaltyAccountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loyalty_transactions_invoiceId ON loyalty_transactions(invoiceId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_loyalty_transactions_invoiceId_txnType ON loyalty_transactions(invoiceId, txnType)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS business_health_metrics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        overallScore INTEGER NOT NULL,
                        metricDetailsJson TEXT NOT NULL,
                        strengthsSummary TEXT NOT NULL,
                        weaknessesSummary TEXT NOT NULL,
                        recordedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_business_health_metrics_businessId ON business_health_metrics(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_business_health_metrics_recordedAtMillis ON business_health_metrics(recordedAtMillis)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        purchasePricePaise INTEGER NOT NULL,
                        sellingPricePaise INTEGER NOT NULL,
                        wholesalePricePaise INTEGER NOT NULL,
                        mrpPaise INTEGER NOT NULL,
                        discountPercentScaled INTEGER NOT NULL,
                        supplierName TEXT NOT NULL,
                        changeSource TEXT NOT NULL,
                        changedAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_businessId ON price_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_productId ON price_history(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_changedAtMillis ON price_history(changedAtMillis)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        supplierId INTEGER,
                        supplierName TEXT NOT NULL,
                        batchNumber TEXT NOT NULL,
                        manufacturingDateMillis INTEGER,
                        expiryDateMillis INTEGER,
                        purchaseDateMillis INTEGER NOT NULL,
                        initialQuantityScaled INTEGER NOT NULL,
                        remainingQuantityScaled INTEGER NOT NULL,
                        unitCostPaise INTEGER NOT NULL,
                        purchaseId INTEGER,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_batches_businessId ON product_batches(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_batches_productId ON product_batches(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_batches_supplierId ON product_batches(supplierId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_batches_expiryDateMillis ON product_batches(expiryDateMillis)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS expiry_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        batchId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        batchNumber TEXT NOT NULL,
                        expiryDateMillis INTEGER NOT NULL,
                        alertLevel TEXT NOT NULL,
                        acknowledged INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(batchId) REFERENCES product_batches(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expiry_alerts_businessId ON expiry_alerts(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expiry_alerts_batchId ON expiry_alerts(batchId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_closings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        closingDateIso TEXT NOT NULL,
                        windowStartMillis INTEGER NOT NULL,
                        windowEndMillis INTEGER NOT NULL,
                        openingCashPaise INTEGER NOT NULL,
                        cashSalesPaise INTEGER NOT NULL,
                        cashReceiptsPaise INTEGER NOT NULL,
                        cashExpensesPaise INTEGER NOT NULL,
                        cashPaymentsPaise INTEGER NOT NULL,
                        cashRefundsPaise INTEGER NOT NULL,
                        expectedClosingCashPaise INTEGER NOT NULL,
                        actualCountedCashPaise INTEGER NOT NULL,
                        cashDifferencePaise INTEGER NOT NULL,
                        upiCollectionsPaise INTEGER NOT NULL,
                        bankCollectionsPaise INTEGER NOT NULL,
                        creditSalesPaise INTEGER NOT NULL,
                        totalSalesPaise INTEGER NOT NULL,
                        totalExpensesPaise INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_closings_businessId ON daily_closings(businessId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_closings_businessId_closingDateIso ON daily_closings(businessId, closingDateIso)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_closing_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dailyClosingId INTEGER NOT NULL,
                        entryType TEXT NOT NULL,
                        label TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        reference TEXT NOT NULL,
                        FOREIGN KEY(dailyClosingId) REFERENCES daily_closings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_closing_entries_dailyClosingId ON daily_closing_entries(dailyClosingId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS backup_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        fileExtension TEXT NOT NULL,
                        safUriString TEXT NOT NULL,
                        triggerType TEXT NOT NULL,
                        isEncrypted INTEGER NOT NULL,
                        checksumSha256 TEXT NOT NULL,
                        recordCount INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        verificationStatus TEXT NOT NULL,
                        restoreStatus TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_backup_history_businessId ON backup_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_backup_history_createdAt ON backup_history(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        sourceFileName TEXT NOT NULL,
                        detectedFormat TEXT NOT NULL,
                        targetEntityType TEXT NOT NULL,
                        duplicateStrategy TEXT NOT NULL,
                        importedCount INTEGER NOT NULL,
                        updatedCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL,
                        errorCount INTEGER NOT NULL,
                        safetyBackupChecksum TEXT NOT NULL,
                        createdRecordIdsJson TEXT NOT NULL,
                        wasUndone INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_history_businessId ON import_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_history_createdAt ON import_history(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        sourceFileName TEXT NOT NULL,
                        detectedFileType TEXT NOT NULL,
                        recoveryMode TEXT NOT NULL,
                        duplicateStrategy TEXT NOT NULL,
                        safetyBackupFile TEXT NOT NULL,
                        recoveredCount INTEGER NOT NULL,
                        unsupportedCount INTEGER NOT NULL,
                        errorCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        reportSummary TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recovery_history_businessId ON recovery_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recovery_history_createdAt ON recovery_history(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recoveryHistoryId INTEGER NOT NULL,
                        recordCategory TEXT NOT NULL,
                        sourceIdentifier TEXT NOT NULL,
                        mappedLocalId INTEGER,
                        status TEXT NOT NULL,
                        message TEXT NOT NULL,
                        FOREIGN KEY(recoveryHistoryId) REFERENCES recovery_history(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recovery_records_recoveryHistoryId ON recovery_records(recoveryHistoryId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        targetType TEXT NOT NULL,
                        columnMapJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_mappings_businessId ON import_mappings(businessId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS invoice_audit_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        invoiceId INTEGER NOT NULL,
                        invoiceNumber TEXT NOT NULL,
                        action TEXT NOT NULL,
                        details TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_audit_logs_businessId ON invoice_audit_logs(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_audit_logs_invoiceId ON invoice_audit_logs(invoiceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_audit_logs_createdAt ON invoice_audit_logs(createdAt)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId INTEGER NOT NULL,
                        module TEXT NOT NULL,
                        query TEXT NOT NULL,
                        searchedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_businessId ON search_history(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_businessId_module_searchedAtMillis ON search_history(businessId, module, searchedAtMillis)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_businessId_module_query ON search_history(businessId, module, query)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN tagline TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN website TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN signatureUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN sealUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_templates ADD COLUMN secondaryColorHex TEXT NOT NULL DEFAULT '#0284C7'")
            }
        }

        @Volatile
        private var INSTANCE: HisabPulseDatabase? = null

        fun getInstance(context: Context): HisabPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HisabPulseDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun ensureSystemSeeded(db: HisabPulseDatabase) = ensureBusinessSeeded(db, 1L)

        suspend fun ensureBusinessSeeded(db: HisabPulseDatabase, businessId: Long = 1L) = withContext(Dispatchers.IO) {
            val core = db.coreDao()
            val inv = db.inventoryDao()
            val acc = db.accountingDao()
            val adv = db.advancedDao()

            if (core.getAppSettingsOnce() == null) {
                core.upsertAppSettings(AppSettings(id = 1, activeBusinessId = businessId))
            }

            if (core.getAllTaxConfigurationsOnce(businessId).isEmpty()) {
                val slabs = listOf(
                    TaxConfiguration(businessId = businessId, name = "GST 0% (Exempt)", ratePercentScaled = 0, cgstPercentScaled = 0, sgstPercentScaled = 0, igstPercentScaled = 0, isDefault = true),
                    TaxConfiguration(businessId = businessId, name = "GST 5%", ratePercentScaled = 500, cgstPercentScaled = 250, sgstPercentScaled = 250, igstPercentScaled = 500),
                    TaxConfiguration(businessId = businessId, name = "GST 12%", ratePercentScaled = 1200, cgstPercentScaled = 600, sgstPercentScaled = 600, igstPercentScaled = 1200),
                    TaxConfiguration(businessId = businessId, name = "GST 18%", ratePercentScaled = 1800, cgstPercentScaled = 900, sgstPercentScaled = 900, igstPercentScaled = 1800),
                    TaxConfiguration(businessId = businessId, name = "GST 28%", ratePercentScaled = 2800, cgstPercentScaled = 1400, sgstPercentScaled = 1400, igstPercentScaled = 2800)
                )
                slabs.forEach { core.insertTaxConfiguration(it) }
            }

            val existingTemplates = core.getAllInvoiceTemplatesOnce(businessId)
            val existingCodes = existingTemplates.map { it.code }.toSet()
            val selectedCode = existingTemplates.firstOrNull { it.isSelected }?.code
            val shouldPromoteCorporateDefault = "CORPORATE_WAVE" !in existingCodes &&
                (selectedCode == null || selectedCode == "MODERN_ORANGE")
            val defaultTemplates = listOf(
                InvoiceTemplate(
                    businessId = businessId,
                    code = "CORPORATE_WAVE",
                    name = "Corporate Wave Navy",
                    description = "Layered navy & blue corporate tax invoice with dual signature & seal cards",
                    accentColorHex = "#0F2D5C",
                    secondaryColorHex = "#0284C7",
                    paperSize = InvoicePaperSize.A4,
                    footerMessage = "Thank You For Your Business!",
                    termsText = "1. Goods once sold will not be taken back.\n2. Payment should be made within the due date.\n3. Thank you for your business.",
                    isSelected = shouldPromoteCorporateDefault
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "MODERN_ORANGE",
                    name = "HisabPulse Modern Orange",
                    description = "Clean commercial invoice with orange header banner & UPI QR",
                    accentColorHex = "#EA580C",
                    secondaryColorHex = "#FB923C",
                    paperSize = InvoicePaperSize.A4,
                    isSelected = false
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "CLASSIC_GST",
                    name = "Classic GST Tax Invoice",
                    description = "Traditional structured A4 GST table with HSN, CGST/SGST/IGST columns",
                    accentColorHex = "#1E293B",
                    secondaryColorHex = "#475569",
                    paperSize = InvoicePaperSize.A4
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "COMPACT_POS",
                    name = "Compact Counter Receipt",
                    description = "Fast shop counter receipt layout ideal for retail & restaurant bills",
                    accentColorHex = "#0F172A",
                    secondaryColorHex = "#334155",
                    paperSize = InvoicePaperSize.THERMAL_80
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "MODERN_RETAIL",
                    name = "Modern Retail Bill",
                    description = "High-contrast retail bill highlighting savings, MRP/discount & UPI QR",
                    accentColorHex = "#0284C7",
                    secondaryColorHex = "#38BDF8",
                    paperSize = InvoicePaperSize.A4
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "WHOLESALE_PRO",
                    name = "Wholesale B2B Pro",
                    description = "B2B distributor invoice with batch/HSN breakdown, due terms & signatory",
                    accentColorHex = "#4F46E5",
                    secondaryColorHex = "#818CF8",
                    paperSize = InvoicePaperSize.A4
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "MINIMAL_CLEAN",
                    name = "Minimal Clean Studio",
                    description = "Airy monochrome A5/A4 layout focused on clean typography",
                    accentColorHex = "#334155",
                    secondaryColorHex = "#64748B",
                    paperSize = InvoicePaperSize.A5
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "DETAILED_GST",
                    name = "Detailed GST Compliance",
                    description = "Comprehensive CGST/SGST/IGST summary box, customer GSTIN & terms",
                    accentColorHex = "#059669",
                    secondaryColorHex = "#10B981",
                    paperSize = InvoicePaperSize.A4
                ),
                InvoiceTemplate(
                    businessId = businessId,
                    code = "THERMAL_58",
                    name = "Thermal Receipt 58mm",
                    description = "Ultra-compact 58mm thermal strip layout for Bluetooth mini printers",
                    accentColorHex = "#18181B",
                    secondaryColorHex = "#52525B",
                    paperSize = InvoicePaperSize.THERMAL_58
                )
            )
            var insertedCorpId: Long? = null
            defaultTemplates.filter { it.code !in existingCodes }.forEach { tpl ->
                val insertedId = core.insertInvoiceTemplate(tpl)
                if (tpl.code == "CORPORATE_WAVE") insertedCorpId = insertedId
            }
            if (shouldPromoteCorporateDefault && insertedCorpId != null) {
                core.selectTemplate(insertedCorpId!!, businessId)
            }

            if (inv.getAllCategoriesOnce(businessId).isEmpty()) {
                listOf("General", "Retail Goods", "Services", "Raw Materials").forEach {
                    inv.insertCategory(ProductCategory(businessId = businessId, name = it))
                }
            }

            if (inv.getAllUnitsOnce(businessId).isEmpty()) {
                listOf(
                    UnitEntity(businessId = businessId, name = "Pieces", shortName = "PCS", baseUnitShortName = "PCS", conversionFactorScaled = 100),
                    UnitEntity(businessId = businessId, name = "Kilograms", shortName = "KG", baseUnitShortName = "GM", conversionFactorScaled = 100000),
                    UnitEntity(businessId = businessId, name = "Grams", shortName = "GM", baseUnitShortName = "GM", conversionFactorScaled = 100),
                    UnitEntity(businessId = businessId, name = "Litres", shortName = "LTR", baseUnitShortName = "ML", conversionFactorScaled = 100000),
                    UnitEntity(businessId = businessId, name = "Box", shortName = "BOX", baseUnitShortName = "PCS", conversionFactorScaled = 1000),
                    UnitEntity(businessId = businessId, name = "Meters", shortName = "MTR", baseUnitShortName = "MTR", conversionFactorScaled = 100),
                    UnitEntity(businessId = businessId, name = "Hours", shortName = "HR", baseUnitShortName = "HR", conversionFactorScaled = 100)
                ).forEach { inv.insertUnit(it) }
            }

            if (inv.getAllWarehousesOnce(businessId).isEmpty()) {
                inv.insertWarehouse(Warehouse(businessId = businessId, name = "Main Store", location = "Primary Location", isDefault = true))
            }

            if (acc.getAllExpenseCategoriesOnce(businessId).isEmpty()) {
                listOf(
                    "Rent", "Electricity", "Fuel", "Transport", "Salary",
                    "Maintenance", "Internet", "Packaging", "Marketing", "Food", "Miscellaneous"
                ).forEach { acc.insertExpenseCategory(ExpenseCategory(businessId = businessId, name = it, isSystemDefault = true)) }
            }

            if (acc.getAllAccountsOnce(businessId).isEmpty()) {
                listOf(
                    Account(businessId = businessId, code = AccountCodes.CASH, name = "Cash in Hand", accountType = AccountType.ASSET, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.BANK, name = "Primary Bank Account", accountType = AccountType.ASSET, isBankAccount = true, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.RECEIVABLES, name = "Accounts Receivable (Customers)", accountType = AccountType.ASSET, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.INVENTORY, name = "Inventory Asset", accountType = AccountType.ASSET, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.INPUT_GST, name = "Input GST Credit", accountType = AccountType.ASSET, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.PAYABLES, name = "Accounts Payable (Suppliers)", accountType = AccountType.LIABILITY, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.OUTPUT_GST, name = "Output GST Payable", accountType = AccountType.LIABILITY, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.CUSTOMER_ADVANCES, name = "Customer Advances", accountType = AccountType.LIABILITY, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.OWNER_EQUITY, name = "Owner Capital & Opening Equity", accountType = AccountType.EQUITY, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.SALES_REVENUE, name = "Sales Revenue", accountType = AccountType.INCOME, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.OTHER_INCOME, name = "Other Income", accountType = AccountType.INCOME, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.COGS, name = "Cost of Goods Sold", accountType = AccountType.EXPENSE, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.PURCHASES, name = "Purchases", accountType = AccountType.EXPENSE, isSystemAccount = true),
                    Account(businessId = businessId, code = AccountCodes.OPERATING_EXPENSES, name = "Operating Expenses", accountType = AccountType.EXPENSE, isSystemAccount = true)
                ).forEach { acc.insertAccount(it) }
            }

            if (adv.getLoyaltyConfigOnce(businessId) == null) {
                adv.upsertLoyaltyConfig(LoyaltyConfiguration(businessId = businessId, isEnabled = true))
            }
        }
    }
}
