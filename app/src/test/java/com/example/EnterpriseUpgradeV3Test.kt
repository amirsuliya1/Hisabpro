package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.EscPosPrinterManager
import com.example.core.ExcelXmlUtils
import com.example.core.InspectedFileFormat
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Customer
import com.example.data.local.entities.DuplicateStrategy
import com.example.data.local.entities.Expense
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.InvoiceTemplate
import com.example.data.local.entities.LoyaltyConfiguration
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.local.entities.Product
import com.example.data.local.entities.RecoveryMode
import com.example.data.local.entities.ThermalPaperWidth
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.AccountingRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.EngagementAndOpsRepository
import com.example.data.repository.ImportTargetType
import com.example.data.repository.IntelligenceRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.ParsedVoiceAction
import com.example.data.repository.PartyRepository
import com.example.data.repository.RecoveryAndImportRepository
import com.example.data.repository.SaveInvoiceRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnterpriseUpgradeV3Test {

    private lateinit var context: Context
    private lateinit var db: HisabPulseDatabase
    private lateinit var prefs: AppPreferencesRepository
    private lateinit var businessRepo: BusinessRepository
    private lateinit var partyRepo: PartyRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var billingRepo: BillingRepository
    private lateinit var accountingRepo: AccountingRepository
    private lateinit var backupRepo: BackupRepository
    private lateinit var intelligenceRepo: IntelligenceRepository
    private lateinit var engagementRepo: EngagementAndOpsRepository
    private lateinit var recoveryRepo: RecoveryAndImportRepository

    private val testDbName = "hisabpulse_v3_enterprise_test.db"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        db = Room.databaseBuilder(context, HisabPulseDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()
        HisabPulseDatabase.ensureSystemSeeded(db)
        prefs = AppPreferencesRepository(context)
        businessRepo = BusinessRepository(db, prefs)
        partyRepo = PartyRepository(db)
        inventoryRepo = InventoryRepository(db)
        billingRepo = BillingRepository(db)
        accountingRepo = AccountingRepository(db)
        backupRepo = BackupRepository(context, db, prefs)
        intelligenceRepo = IntelligenceRepository(db)
        engagementRepo = EngagementAndOpsRepository(db, billingRepo, accountingRepo)
        recoveryRepo = RecoveryAndImportRepository(context, db, backupRepo, businessRepo)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        context.deleteDatabase(testDbName)
        context.deleteDatabase("hisabpulse_migration_v2_v3.db")
    }

    @Test
    fun `online Firebase permissions and 9 seeded templates with custom template CRUD`() = runBlocking {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertTrue("App requires internet for Firebase Auth and Firestore", perms.contains("android.permission.INTERNET"))
        assertTrue("App requires network state for Firebase", perms.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals(9, businessRepo.invoiceTemplates.first().size)

        val customId = businessRepo.createCustomInvoiceTemplate(
            InvoiceTemplate(
                code = "CUSTOM_1",
                name = "My Custom Template",
                description = "User created template",
                accentColorHex = "#3B82F6"
            )
        )
        assertTrue(customId > 0L)
        val updatedList = businessRepo.invoiceTemplates.first()
        val customTpl = updatedList.first { it.id == customId }
        assertEquals("My Custom Template", customTpl.name)

        businessRepo.deleteCustomInvoiceTemplate(customTpl)
        val finalList = businessRepo.invoiceTemplates.first()
        assertFalse(finalList.any { it.id == customId })
    }

    @Test
    fun `room v2 to v3 migration preserves existing business data and builds all 21 v3 tables`() = runBlocking {
        val migName = "hisabpulse_migration_v2_v3.db"
        context.deleteDatabase(migName)
        val dbFile = context.getDatabasePath(migName)
        dbFile.parentFile?.mkdirs()

        // Create authentic v2 SQLite schema snapshot across all 24 v2 tables before opening Room v3
        val legacySql = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        val v2Statements = listOf(
            """CREATE TABLE IF NOT EXISTS `business_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `businessName` TEXT NOT NULL, `ownerName` TEXT NOT NULL, `logoUri` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, `city` TEXT NOT NULL, `state` TEXT NOT NULL, `pinCode` TEXT NOT NULL, `category` TEXT NOT NULL, `isGstRegistered` INTEGER NOT NULL, `gstin` TEXT NOT NULL, `financialYear` TEXT NOT NULL, `currencySymbol` TEXT NOT NULL, `invoicePrefix` TEXT NOT NULL, `nextInvoiceNumber` INTEGER NOT NULL, `upiId` TEXT NOT NULL, `signatureLabel` TEXT NOT NULL, `defaultTerms` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS `tax_configurations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `ratePercentScaled` INTEGER NOT NULL, `cgstPercentScaled` INTEGER NOT NULL, `sgstPercentScaled` INTEGER NOT NULL, `igstPercentScaled` INTEGER NOT NULL, `hsnSacDefault` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS `invoice_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `showLogo` INTEGER NOT NULL, `showGstin` INTEGER NOT NULL, `showHsn` INTEGER NOT NULL, `showSignature` INTEGER NOT NULL, `showUpiQr` INTEGER NOT NULL, `footerMessage` TEXT NOT NULL, `isSelected` INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS `app_settings` (`id` INTEGER NOT NULL, `themeMode` TEXT NOT NULL, `languageCode` TEXT NOT NULL, `allowNegativeStock` INTEGER NOT NULL, `enableRoundOff` INTEGER NOT NULL, `defaultTaxInclusive` INTEGER NOT NULL, `posQuickPayMode` TEXT NOT NULL, `enableWholesaleModule` INTEGER NOT NULL, `enableMedicalModule` INTEGER NOT NULL, `enableGarmentsModule` INTEGER NOT NULL, `enableElectronicsModule` INTEGER NOT NULL, `enableRestaurantModule` INTEGER NOT NULL, `enableManufacturingModule` INTEGER NOT NULL, `enableServiceModule` INTEGER NOT NULL, `pinLockEnabled` INTEGER NOT NULL, `biometricEnabled` INTEGER NOT NULL, `autoLockMinutes` INTEGER NOT NULL, `hideAmounts` INTEGER NOT NULL, `onboardingCompleted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
            """CREATE TABLE IF NOT EXISTS `backup_metadata` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, `safUriString` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, `isEncrypted` INTEGER NOT NULL, `checksumSha256` TEXT NOT NULL, `recordCount` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `action` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` INTEGER NOT NULL, `summary` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)""",
            """CREATE INDEX IF NOT EXISTS `index_audit_logs_entityType` ON `audit_logs` (`entityType`)""",
            """CREATE INDEX IF NOT EXISTS `index_audit_logs_createdAt` ON `audit_logs` (`createdAt`)""",
            """CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, `state` TEXT NOT NULL, `gstin` TEXT NOT NULL, `category` TEXT NOT NULL, `openingBalancePaise` INTEGER NOT NULL, `currentBalancePaise` INTEGER NOT NULL, `advanceBalancePaise` INTEGER NOT NULL, `creditLimitPaise` INTEGER NOT NULL, `defaultDueDays` INTEGER NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""",
            """CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)""",
            """CREATE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`)""",
            """CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, `state` TEXT NOT NULL, `gstin` TEXT NOT NULL, `openingBalancePaise` INTEGER NOT NULL, `currentBalancePaise` INTEGER NOT NULL, `advanceBalancePaise` INTEGER NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""",
            """CREATE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)""",
            """CREATE INDEX IF NOT EXISTS `index_suppliers_phone` ON `suppliers` (`phone`)""",
            """CREATE TABLE IF NOT EXISTS `product_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_product_categories_name` ON `product_categories` (`name`)""",
            """CREATE TABLE IF NOT EXISTS `units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT NOT NULL, `baseUnitShortName` TEXT NOT NULL, `conversionFactorScaled` INTEGER NOT NULL)""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_units_shortName` ON `units` (`shortName`)""",
            """CREATE TABLE IF NOT EXISTS `warehouses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `location` TEXT NOT NULL, `isDefault` INTEGER NOT NULL)""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_warehouses_name` ON `warehouses` (`name`)""",
            """CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `imageUri` TEXT NOT NULL, `sku` TEXT NOT NULL, `barcode` TEXT NOT NULL, `productCode` TEXT NOT NULL, `hsnSacCode` TEXT NOT NULL, `categoryId` INTEGER, `categoryName` TEXT NOT NULL, `brand` TEXT NOT NULL, `description` TEXT NOT NULL, `purchasePricePaise` INTEGER NOT NULL, `sellingPricePaise` INTEGER NOT NULL, `wholesalePricePaise` INTEGER NOT NULL, `wholesaleMinQtyScaled` INTEGER NOT NULL, `mrpPaise` INTEGER NOT NULL, `taxRatePercentScaled` INTEGER NOT NULL, `isTaxInclusive` INTEGER NOT NULL, `unitId` INTEGER, `unitName` TEXT NOT NULL, `openingStockScaled` INTEGER NOT NULL, `currentStockScaled` INTEGER NOT NULL, `minStockScaled` INTEGER NOT NULL, `batchNumber` TEXT NOT NULL, `expiryDateMillis` INTEGER, `garmentSize` TEXT NOT NULL, `garmentColor` TEXT NOT NULL, `garmentStyle` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, `warrantyMonths` INTEGER NOT NULL, `isServiceItem` INTEGER NOT NULL, `isRawMaterial` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `product_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`unitId`) REFERENCES `units`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)""",
            """CREATE INDEX IF NOT EXISTS `index_products_unitId` ON `products` (`unitId`)""",
            """CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)""",
            """CREATE INDEX IF NOT EXISTS `index_products_sku` ON `products` (`sku`)""",
            """CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)""",
            """CREATE TABLE IF NOT EXISTS `stock_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `warehouseId` INTEGER, `movementType` TEXT NOT NULL, `quantityDeltaScaled` INTEGER NOT NULL, `resultingStockScaled` INTEGER NOT NULL, `unitCostPaise` INTEGER NOT NULL, `referenceType` TEXT NOT NULL, `referenceId` INTEGER, `batchNumber` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`warehouseId`) REFERENCES `warehouses`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movements_warehouseId` ON `stock_movements` (`warehouseId`)""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movements_createdAt` ON `stock_movements` (`createdAt`)""",
            """CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceNumber` TEXT NOT NULL, `documentType` TEXT NOT NULL, `status` TEXT NOT NULL, `invoiceDateMillis` INTEGER NOT NULL, `dueDateMillis` INTEGER, `customerId` INTEGER, `customerName` TEXT NOT NULL, `customerPhone` TEXT NOT NULL, `customerAddress` TEXT NOT NULL, `customerGstin` TEXT NOT NULL, `isInterState` INTEGER NOT NULL, `subtotalPaise` INTEGER NOT NULL, `itemDiscountPaise` INTEGER NOT NULL, `invoiceDiscountPercentScaled` INTEGER NOT NULL, `invoiceDiscountFlatPaise` INTEGER NOT NULL, `taxableAmountPaise` INTEGER NOT NULL, `cgstPaise` INTEGER NOT NULL, `sgstPaise` INTEGER NOT NULL, `igstPaise` INTEGER NOT NULL, `totalTaxPaise` INTEGER NOT NULL, `additionalChargesPaise` INTEGER NOT NULL, `shippingChargesPaise` INTEGER NOT NULL, `roundOffPaise` INTEGER NOT NULL, `grandTotalPaise` INTEGER NOT NULL, `paidAmountPaise` INTEGER NOT NULL, `balanceDuePaise` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `tableNumber` TEXT NOT NULL, `kotStatus` TEXT NOT NULL, `linkedOriginalInvoiceId` INTEGER, `notes` TEXT NOT NULL, `termsAndConditions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_invoiceNumber` ON `invoices` (`invoiceNumber`)""",
            """CREATE INDEX IF NOT EXISTS `index_invoices_customerId` ON `invoices` (`customerId`)""",
            """CREATE INDEX IF NOT EXISTS `index_invoices_invoiceDateMillis` ON `invoices` (`invoiceDateMillis`)""",
            """CREATE INDEX IF NOT EXISTS `index_invoices_status` ON `invoices` (`status`)""",
            """CREATE TABLE IF NOT EXISTS `invoice_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceId` INTEGER NOT NULL, `productId` INTEGER, `productName` TEXT NOT NULL, `productCode` TEXT NOT NULL, `hsnSacCode` TEXT NOT NULL, `unitName` TEXT NOT NULL, `quantityScaled` INTEGER NOT NULL, `ratePaise` INTEGER NOT NULL, `purchaseCostPaise` INTEGER NOT NULL, `discountPercentScaled` INTEGER NOT NULL, `discountAmountPaise` INTEGER NOT NULL, `taxRatePercentScaled` INTEGER NOT NULL, `isTaxInclusive` INTEGER NOT NULL, `taxableAmountPaise` INTEGER NOT NULL, `cgstPaise` INTEGER NOT NULL, `sgstPaise` INTEGER NOT NULL, `igstPaise` INTEGER NOT NULL, `lineTotalPaise` INTEGER NOT NULL, `batchNumber` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE INDEX IF NOT EXISTS `index_invoice_items_invoiceId` ON `invoice_items` (`invoiceId`)""",
            """CREATE INDEX IF NOT EXISTS `index_invoice_items_productId` ON `invoice_items` (`productId`)""",
            """CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseNumber` TEXT NOT NULL, `supplierBillNumber` TEXT NOT NULL, `documentType` TEXT NOT NULL, `status` TEXT NOT NULL, `purchaseDateMillis` INTEGER NOT NULL, `supplierId` INTEGER, `supplierName` TEXT NOT NULL, `supplierGstin` TEXT NOT NULL, `isInterState` INTEGER NOT NULL, `subtotalPaise` INTEGER NOT NULL, `discountPaise` INTEGER NOT NULL, `taxableAmountPaise` INTEGER NOT NULL, `cgstPaise` INTEGER NOT NULL, `sgstPaise` INTEGER NOT NULL, `igstPaise` INTEGER NOT NULL, `totalTaxPaise` INTEGER NOT NULL, `additionalChargesPaise` INTEGER NOT NULL, `grandTotalPaise` INTEGER NOT NULL, `paidAmountPaise` INTEGER NOT NULL, `balanceDuePaise` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `linkedOriginalPurchaseId` INTEGER, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_purchaseNumber` ON `purchases` (`purchaseNumber`)""",
            """CREATE INDEX IF NOT EXISTS `index_purchases_supplierId` ON `purchases` (`supplierId`)""",
            """CREATE INDEX IF NOT EXISTS `index_purchases_purchaseDateMillis` ON `purchases` (`purchaseDateMillis`)""",
            """CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `productId` INTEGER, `productName` TEXT NOT NULL, `hsnSacCode` TEXT NOT NULL, `unitName` TEXT NOT NULL, `quantityScaled` INTEGER NOT NULL, `ratePaise` INTEGER NOT NULL, `discountPercentScaled` INTEGER NOT NULL, `taxRatePercentScaled` INTEGER NOT NULL, `taxableAmountPaise` INTEGER NOT NULL, `cgstPaise` INTEGER NOT NULL, `sgstPaise` INTEGER NOT NULL, `igstPaise` INTEGER NOT NULL, `lineTotalPaise` INTEGER NOT NULL, `batchNumber` TEXT NOT NULL, `expiryDateMillis` INTEGER, FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)""",
            """CREATE INDEX IF NOT EXISTS `index_purchase_items_productId` ON `purchase_items` (`productId`)""",
            """CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `accountType` TEXT NOT NULL, `isBankAccount` INTEGER NOT NULL, `bankAccountNumber` TEXT NOT NULL, `bankIfsc` TEXT NOT NULL, `upiId` TEXT NOT NULL, `openingBalancePaise` INTEGER NOT NULL, `currentBalancePaise` INTEGER NOT NULL, `isSystemAccount` INTEGER NOT NULL)""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_code` ON `accounts` (`code`)""",
            """CREATE TABLE IF NOT EXISTS `payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptNumber` TEXT NOT NULL, `direction` TEXT NOT NULL, `paymentMode` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `paymentDateMillis` INTEGER NOT NULL, `customerId` INTEGER, `supplierId` INTEGER, `partyName` TEXT NOT NULL, `accountId` INTEGER, `referenceNumber` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE INDEX IF NOT EXISTS `index_payments_customerId` ON `payments` (`customerId`)""",
            """CREATE INDEX IF NOT EXISTS `index_payments_supplierId` ON `payments` (`supplierId`)""",
            """CREATE INDEX IF NOT EXISTS `index_payments_paymentDateMillis` ON `payments` (`paymentDateMillis`)""",
            """CREATE TABLE IF NOT EXISTS `payment_allocations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `paymentId` INTEGER NOT NULL, `invoiceId` INTEGER, `purchaseId` INTEGER, `allocatedAmountPaise` INTEGER NOT NULL, FOREIGN KEY(`paymentId`) REFERENCES `payments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_payment_allocations_paymentId` ON `payment_allocations` (`paymentId`)""",
            """CREATE INDEX IF NOT EXISTS `index_payment_allocations_invoiceId` ON `payment_allocations` (`invoiceId`)""",
            """CREATE INDEX IF NOT EXISTS `index_payment_allocations_purchaseId` ON `payment_allocations` (`purchaseId`)""",
            """CREATE TABLE IF NOT EXISTS `expense_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isSystemDefault` INTEGER NOT NULL)""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_categories_name` ON `expense_categories` (`name`)""",
            """CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER, `categoryName` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `expenseDateMillis` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `accountId` INTEGER, `description` TEXT NOT NULL, `receiptUri` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `expense_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            """CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)""",
            """CREATE INDEX IF NOT EXISTS `index_expenses_expenseDateMillis` ON `expenses` (`expenseDateMillis`)""",
            """CREATE TABLE IF NOT EXISTS `journal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryNumber` TEXT NOT NULL, `entryDateMillis` INTEGER NOT NULL, `description` TEXT NOT NULL, `referenceType` TEXT NOT NULL, `referenceId` INTEGER, `totalDebitPaise` INTEGER NOT NULL, `totalCreditPaise` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)""",
            """CREATE INDEX IF NOT EXISTS `index_journal_entries_entryDateMillis` ON `journal_entries` (`entryDateMillis`)""",
            """CREATE INDEX IF NOT EXISTS `index_journal_entries_referenceType_referenceId` ON `journal_entries` (`referenceType`, `referenceId`)""",
            """CREATE TABLE IF NOT EXISTS `ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `journalEntryId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `accountName` TEXT NOT NULL, `entryDateMillis` INTEGER NOT NULL, `debitPaise` INTEGER NOT NULL, `creditPaise` INTEGER NOT NULL, `partyType` TEXT NOT NULL, `partyId` INTEGER, `narration` TEXT NOT NULL, FOREIGN KEY(`journalEntryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
            """CREATE INDEX IF NOT EXISTS `index_ledger_entries_journalEntryId` ON `ledger_entries` (`journalEntryId`)""",
            """CREATE INDEX IF NOT EXISTS `index_ledger_entries_accountId` ON `ledger_entries` (`accountId`)""",
            """CREATE INDEX IF NOT EXISTS `index_ledger_entries_entryDateMillis` ON `ledger_entries` (`entryDateMillis`)""",
            "INSERT INTO business_profiles VALUES (1,'Legacy v2 Mart','Kiran','','9999988888','','','Mumbai','Maharashtra','400001','RETAIL',1,'27AAPFU0939F1ZV','FY 2026-27','₹','LGC-',101,'','Owner','Thanks',1000)",
            "INSERT INTO app_settings VALUES (1,'LIGHT','en',0,1,0,'CASH',0,0,0,0,0,0,0,0,0,5,0,1,1000)",
            "INSERT INTO customers (name,phone,email,address,state,gstin,category,openingBalancePaise,currentBalancePaise,advanceBalancePaise,creditLimitPaise,defaultDueDays,notes,isActive,createdAt,updatedAt) VALUES ('Legacy Customer','9800000001','','','Maharashtra','','Regular',0,0,0,0,15,'',1,1000,1000)"
        )
        v2Statements.forEach { legacySql.execSQL(it) }
        legacySql.version = 2
        legacySql.close()

        val migratedDb = Room.databaseBuilder(context, HisabPulseDatabase::class.java, migName)
            .addMigrations(
                HisabPulseDatabase.MIGRATION_1_2,
                HisabPulseDatabase.MIGRATION_2_3,
                HisabPulseDatabase.MIGRATION_3_4,
                HisabPulseDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()

        val profile = migratedDb.coreDao().getBusinessProfileByIdOnce(1L)
        assertNotNull(profile)
        assertEquals("Legacy v2 Mart", profile!!.businessName)
        assertEquals(1, migratedDb.partyDao().getAllCustomersOnce(1L).size)

        // Confirm v3 & v4 tables exist via SQLite master inspection
        val sdb = migratedDb.openHelper.writableDatabase
        val cursor = sdb.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            tableNames += cursor.getString(0)
        }
        cursor.close()

        val expectedV3Tables = listOf(
            "stock_predictions", "reorder_suggestions", "reminder_history",
            "printer_configurations", "upi_configurations", "loyalty_configurations",
            "loyalty_accounts", "loyalty_transactions", "business_health_metrics",
            "price_history", "product_batches", "expiry_alerts", "daily_closings",
            "daily_closing_entries", "voice_command_history", "import_history",
            "import_mappings", "recovery_history", "recovery_records",
            "backup_history", "invoice_audit_logs", "search_history"
        )
        expectedV3Tables.forEach { tbl ->
            assertTrue("Missing table $tbl after v2->v4 migrations", tbl in tableNames)
        }
        migratedDb.close()
    }

    @Test
    fun `multi business isolation keeps products customers and invoices strictly separated`() = runBlocking {
        businessRepo.saveBusinessProfile(BusinessProfile(businessName = "Alpha Retail"), completeOnboarding = true)
        val p1 = inventoryRepo.saveProduct(Product(name = "Alpha Item", sellingPricePaise = 100_00L))
        assertTrue(p1 > 0L)
        assertEquals(1, inventoryRepo.activeProducts.first().size)

        val biz2Id = businessRepo.createBusinessProfile(
            BusinessProfile(businessName = "Beta Pharmacy", invoicePrefix = "BP-"),
            switchToNewBusiness = true
        )
        assertTrue(biz2Id > 1L)
        assertEquals("Beta Pharmacy", businessRepo.businessProfile.first()?.businessName)
        assertTrue("Switched business must start empty", inventoryRepo.activeProducts.first().isEmpty())

        inventoryRepo.saveProduct(Product(name = "Beta Med", sellingPricePaise = 250_00L))
        assertEquals(1, inventoryRepo.activeProducts.first().size)
        assertEquals("Beta Med", inventoryRepo.activeProducts.first().first().name)

        businessRepo.switchActiveBusiness(1L).getOrThrow()
        assertEquals("Alpha Retail", businessRepo.businessProfile.first()?.businessName)
        assertEquals("Alpha Item", inventoryRepo.activeProducts.first().first().name)
    }

    @Test
    fun `batch expiry warning override and block modes`() = runBlocking {
        val prodId = inventoryRepo.saveProduct(
            Product(
                name = "Paracetamol 500mg",
                sellingPricePaise = 50_00L,
                purchasePricePaise = 30_00L,
                taxRatePercentScaled = 1200L,
                trackExpiry = true
            )
        )
        val pastMillis = System.currentTimeMillis() - 10 * 86_400_000L
        val batchId = intelligenceRepo.addProductBatch(
            productId = prodId,
            batchNumber = "B-EXP-01",
            expiryDateMillis = pastMillis,
            manufacturingDateMillis = null,
            quantityScaled = 10_00L,
            unitCostPaise = 30_00L
        ).getOrThrow()

        // Default warnExpiredBatchSale = true, blockExpiredBatchSale = false
        val warnAttempt = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                items = listOf(
                    InvoiceLineDraft(
                        productId = prodId,
                        batchId = batchId,
                        productName = "Paracetamol 500mg",
                        quantity = BigDecimal("2"),
                        rate = BigDecimal("50"),
                        purchaseCost = BigDecimal("30"),
                        taxRatePercent = BigDecimal("12"),
                        batchNumber = "B-EXP-01",
                        expiryDateMillis = pastMillis
                    )
                )
            )
        )
        assertTrue(warnAttempt.isFailure)
        assertTrue(warnAttempt.exceptionOrNull()?.message?.startsWith("EXPIRED_BATCH_WARNING:") == true)

        // Override succeeds and decrements batch quantity 10 -> 8
        val overrideInvId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                allowExpiredBatchOverride = true,
                items = listOf(
                    InvoiceLineDraft(
                        productId = prodId,
                        batchId = batchId,
                        productName = "Paracetamol 500mg",
                        quantity = BigDecimal("2"),
                        rate = BigDecimal("50"),
                        purchaseCost = BigDecimal("30"),
                        taxRatePercent = BigDecimal("12"),
                        batchNumber = "B-EXP-01",
                        expiryDateMillis = pastMillis
                    )
                )
            )
        ).getOrThrow()
        assertTrue(overrideInvId > 0L)
        val updatedBatch = intelligenceRepo.observeBatchesForProduct(prodId).first().first { it.id == batchId }
        assertEquals(8_00L, updatedBatch.remainingQuantityScaled)

        // Enable strict blockExpiredBatchSale -> blocks even with override flag
        businessRepo.updateAppSettings { it.copy(blockExpiredBatchSale = true) }
        val blockedAttempt = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                allowExpiredBatchOverride = true,
                items = listOf(
                    InvoiceLineDraft(
                        productId = prodId,
                        batchId = batchId,
                        productName = "Paracetamol 500mg",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("50"),
                        batchNumber = "B-EXP-01",
                        expiryDateMillis = pastMillis
                    )
                )
            )
        )
        assertTrue(blockedAttempt.isFailure)
        assertTrue(blockedAttempt.exceptionOrNull()?.message?.contains("blocked in settings") == true)
    }

    @Test
    fun `loyalty earn redeem tier progression and balanced double entry`() = runBlocking {
        engagementRepo.saveLoyaltyConfig(
            LoyaltyConfiguration(
                isEnabled = true,
                rupeesPerPoint = 100L,
                pointValuePaise = 100L, // ₹1 per point
                minInvoiceAmountPaise = 100_00L,
                minRedeemPoints = 5L,
                maxRedeemPointsPerInvoice = 50L
            )
        )
        val cid = partyRepo.saveCustomer(Customer(name = "Priya Sharma", phone = "9876500001"))
        val pid = inventoryRepo.saveProduct(
            Product(name = "LED Floodlight", sellingPricePaise = 1000_00L, purchasePricePaise = 600_00L, openingStockScaled = 20_00L)
        )

        // Invoice 1: ₹1000 -> earns 10 points (rupeesPerPoint=100)
        val inv1Id = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                customerId = cid,
                customerName = "Priya Sharma",
                paidAmount = BigDecimal("1000.00"),
                items = listOf(
                    InvoiceLineDraft(productId = pid, productName = "LED Floodlight", quantity = BigDecimal("1"), rate = BigDecimal("1000"), purchaseCost = BigDecimal("600"))
                )
            )
        ).getOrThrow()

        val inv1 = billingRepo.getInvoiceWithItemsOnce(inv1Id)!!.first
        assertEquals(10L, inv1.loyaltyPointsEarned)
        assertEquals(10L, engagementRepo.loyaltyAccounts.first().first { it.customerId == cid }.pointsBalance)

        // Manual negative overdraw rejected
        val badAdjust = engagementRepo.adjustLoyaltyPointsManually(cid, -50L, "Invalid overdraw")
        assertTrue(badAdjust.isFailure)

        // Invoice 2: Redeem 5 points (₹5 discount on ₹1000 = ₹995 total)
        val inv2Id = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                customerId = cid,
                customerName = "Priya Sharma",
                redeemLoyaltyPoints = 5L,
                paidAmount = BigDecimal("995.00"),
                items = listOf(
                    InvoiceLineDraft(productId = pid, productName = "LED Floodlight", quantity = BigDecimal("1"), rate = BigDecimal("1000"), purchaseCost = BigDecimal("600"))
                )
            )
        ).getOrThrow()

        val inv2 = billingRepo.getInvoiceWithItemsOnce(inv2Id)!!.first
        assertEquals(5L, inv2.loyaltyPointsRedeemed)
        assertEquals(5_00L, inv2.loyaltyDiscountPaise)
        assertEquals(995_00L, inv2.grandTotalPaise)

        // Every journal remains balanced
        val journals = accountingRepo.journalEntries.first()
        assertTrue(journals.isNotEmpty())
        journals.forEach { assertEquals(it.totalDebitPaise, it.totalCreditPaise) }
    }

    @Test
    fun `voice command parser requires manual confirmation before saving expense or draft invoice`() = runBlocking {
        val parsedExp = engagementRepo.parseVoiceCommand("Add 500 rupees transport expense")
        assertTrue(parsedExp is ParsedVoiceAction.CreateExpenseDraft)
        val expDraft = parsedExp as ParsedVoiceAction.CreateExpenseDraft
        assertEquals(BigDecimal("500"), expDraft.amountRupees)
        assertEquals("Transport", expDraft.category)

        val parsedInv = engagementRepo.parseVoiceCommand("Add 5 LED bulbs")
        assertTrue(parsedInv is ParsedVoiceAction.CreateInvoiceDraft)
        val invDraft = parsedInv as ParsedVoiceAction.CreateInvoiceDraft
        assertEquals(BigDecimal("5"), invDraft.qty)
        assertEquals("LED bulbs", invDraft.itemQuery)

        // Parsing alone must NOT mutate accounting or billing DB
        assertTrue(accountingRepo.expenses.first().isEmpty())
        assertTrue(billingRepo.allInvoices.first().isEmpty())

        // After user explicitly confirms:
        engagementRepo.executeConfirmedVoiceExpense(expDraft.amountRupees, expDraft.category, "Auto fare", "Add 500 rupees transport expense").getOrThrow()
        assertEquals(1, accountingRepo.expenses.first().size)

        val draftInvId = engagementRepo.executeConfirmedVoiceDraftInvoice(invDraft.itemQuery, invDraft.qty, "Walk-in Customer", "Add 5 LED bulbs").getOrThrow()
        val createdDraft = billingRepo.getInvoiceWithItemsOnce(draftInvId)!!.first
        assertEquals(InvoiceStatus.DRAFT, createdDraft.status)
    }

    @Test
    fun `stock prediction profit intelligence and daily closing accuracy`() = runBlocking {
        val pid = inventoryRepo.saveProduct(
            Product(
                name = "USB-C Cable",
                categoryName = "Accessories",
                purchasePricePaise = 100_00L,
                sellingPricePaise = 200_00L,
                openingStockScaled = 50_00L,
                minStockScaled = 5_00L
            )
        )
        // Before 3 sales: INSUFFICIENT_DATA
        val initialPred = intelligenceRepo.computeStockPredictions().first { it.product.id == pid }
        assertEquals("INSUFFICIENT_DATA", initialPred.velocityClass)

        // Create 3 cash sales + 1 loss-making sale
        repeat(3) {
            billingRepo.saveOrFinalizeInvoice(
                SaveInvoiceRequest(
                    paidAmount = BigDecimal("400.00"),
                    paymentMode = PaymentMode.CASH,
                    items = listOf(
                        InvoiceLineDraft(
                            productId = pid,
                            productName = "USB-C Cable",
                            quantity = BigDecimal("2"),
                            rate = BigDecimal("200"),
                            purchaseCost = BigDecimal("100")
                        )
                    )
                )
            ).getOrThrow()
        }
        // Loss-making invoice: sold below cost (₹80 < ₹100)
        billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                paidAmount = BigDecimal("80.00"),
                paymentMode = PaymentMode.CASH,
                items = listOf(
                    InvoiceLineDraft(
                        productId = pid,
                        productName = "USB-C Cable",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("80"),
                        purchaseCost = BigDecimal("100")
                    )
                )
            )
        ).getOrThrow()

        accountingRepo.saveExpense(Expense(categoryName = "Tea", amountPaise = 50_00L, paymentMode = PaymentMode.CASH)).getOrThrow()

        val pred = intelligenceRepo.computeStockPredictions().first { it.product.id == pid }
        assertTrue(pred.velocityClass != "INSUFFICIENT_DATA")
        assertTrue(pred.estimatedDaysRemaining >= 0)

        intelligenceRepo.setManualReorderOverride(pid, 25_00L)
        val afterOverride = intelligenceRepo.computeStockPredictions().first { it.product.id == pid }
        assertEquals(25_00L, afterOverride.manualOverrideScaled)
        assertEquals(25_00L, afterOverride.suggestedReorderScaled)

        val profit = intelligenceRepo.computeProfitIntelligence()
        // Revenue: 3*400 + 80 = 1280; COGS: 7*100 = 700; Gross = 580; OpEx = 50 -> Net = 530
        assertEquals(1280_00L, profit.totalRevenuePaise)
        assertEquals(700_00L, profit.totalCogsPaise)
        assertEquals(580_00L, profit.grossProfitPaise)
        assertEquals(530_00L, profit.netProfitPaise)
        assertEquals(1, profit.lossMakingInvoices.size)

        // Daily closing check & duplicate date guard
        val preview = engagementRepo.buildDailyClosingPreview()
        assertEquals(1280_00L, preview.cashSalesPaise)
        assertEquals(50_00L, preview.cashExpensesPaise)
        val closingId = engagementRepo.submitDailyClosing(preview, preview.expectedClosingCashPaise - 10_00L, "Short by Rs 10").getOrThrow()
        assertTrue(closingId > 0L)
        val savedClosing = engagementRepo.dailyClosings.first().first { it.id == closingId }
        assertEquals(-10_00L, savedClosing.cashDifferencePaise)

        val dupClosing = engagementRepo.submitDailyClosing(engagementRepo.buildDailyClosingPreview(), preview.expectedClosingCashPaise)
        assertTrue(dupClosing.isFailure)

        val closingPdf = PdfGenerator.generateDailyClosingPdf(context, businessRepo.businessProfile.first(), savedClosing)
        assertTrue(closingPdf.exists())
        assertEquals("%PDF-", closingPdf.readBytes().copyOfRange(0, 5).toString(Charsets.US_ASCII))
    }

    @Test
    fun `xlsx roundtrip guided import undo universal recovery and escpos printing`() = runBlocking {
        // 1. XLSX Export & Parse round-trip
        val xlsxBytes = ExcelXmlUtils.writeXlsxBytes(
            sheetName = "Catalog",
            headers = listOf("Product Name", "Selling Price", "Purchase Price", "Stock", "Unit"),
            rows = listOf(
                listOf("HDMI Cable 2m", "350", "200", "15", "PCS"),
                listOf("Wireless Mouse", "650", "400", "8", "PCS")
            )
        )
        val parsed = recoveryRepo.parseTabularBytes("catalog.xlsx", xlsxBytes).getOrThrow()
        assertEquals(2, parsed.rows.size)

        val autoMap = recoveryRepo.autoSuggestColumnMapping(parsed.headers, ImportTargetType.PRODUCTS)
        val preview = recoveryRepo.previewAndValidateTabularImport(parsed, ImportTargetType.PRODUCTS, autoMap)
        assertEquals(2, preview.validRowIndices.size)
        assertTrue(preview.errors.isEmpty())

        val importHist = recoveryRepo.executeTabularImport(
            sourceFileName = "catalog.xlsx",
            table = parsed,
            targetType = ImportTargetType.PRODUCTS,
            columnMap = autoMap,
            duplicateStrategy = DuplicateStrategy.SKIP
        ).getOrThrow()
        assertEquals(2, importHist.importedCount)
        assertEquals(2, inventoryRepo.activeProducts.first().size)

        // Undo import -> soft deletes imported products
        recoveryRepo.undoRecentImport(importHist).getOrThrow()
        assertTrue(inventoryRepo.activeProducts.first().isEmpty())

        // 2. Universal File Inspector & Honest .vyp notice
        val vypInspection = recoveryRepo.inspectRecoveryFile("shop_backup.vyp", ByteArray(32) { 0x42 })
        assertEquals(InspectedFileFormat.VYP_CONTAINER, vypInspection.detectedFormat)
        assertFalse(vypInspection.canDirectlyRecover)
        assertTrue(vypInspection.honestLimitationNote.contains("CSV") || vypInspection.honestLimitationNote.contains("Excel"))

        // 3. Recovery MERGE_IMPORT non-destructive backup merge
        inventoryRepo.saveProduct(Product(name = "HDMI Cable 2m", sellingPricePaise = 350_00L))
        val backupBytes = backupRepo.createBackupBytes(encrypt = true, passphrase = "RecoverKey#1")
        val inspectedBackup = recoveryRepo.inspectRecoveryFile("store.bizmgr", backupBytes)
        assertEquals(InspectedFileFormat.HISABP_ENCRYPTED, inspectedBackup.detectedFormat)

        val existingProdId = inventoryRepo.saveProduct(Product(name = "Existing Local Only Item", sellingPricePaise = 120_00L))
        assertTrue(existingProdId > 0L)
        val recoveryRes = recoveryRepo.executeUniversalRecovery(
            fileName = "store.bizmgr",
            bytes = backupBytes,
            passphrase = "RecoverKey#1",
            mode = RecoveryMode.MERGE_IMPORT,
            duplicateStrategy = DuplicateStrategy.SKIP
        ).getOrThrow()
        assertEquals("COMPLETED", recoveryRes.status)
        assertTrue(inventoryRepo.activeProducts.first().any { it.name == "Existing Local Only Item" })

        // 4. ESC/POS 58mm & 80mm + All 8 PDF templates
        val invId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                paidAmount = BigDecimal("120"),
                items = listOf(InvoiceLineDraft(productName = "Existing Local Only Item", quantity = BigDecimal.ONE, rate = BigDecimal("120")))
            )
        ).getOrThrow()
        val (inv, invItems) = billingRepo.getInvoiceWithItemsOnce(invId)!!
        val r58 = EscPosPrinterManager.buildInvoiceReceiptText(null, inv, invItems, PrinterConfiguration(paperWidth = ThermalPaperWidth.MM_58))
        val r80 = EscPosPrinterManager.buildInvoiceReceiptText(null, inv, invItems, PrinterConfiguration(paperWidth = ThermalPaperWidth.MM_80))
        assertTrue(r58.contains("Existing Local Only Item"))
        assertTrue(r80.lines().first().length >= r58.lines().first().length)

        val escBytes = EscPosPrinterManager.buildEscPosBytes(r58, "upi://pay?pa=test@upi")
        assertEquals(0x1B.toByte(), escBytes[0])
        assertEquals(0x40.toByte(), escBytes[1])

        val allTemplates = businessRepo.invoiceTemplates.first()
        assertEquals(9, allTemplates.size)
        allTemplates.forEach { tpl ->
            val pdf = PdfGenerator.generateInvoicePdf(context, null, inv, invItems, tpl)
            assertTrue("Template ${tpl.code} PDF missing", pdf.exists())
            assertEquals("%PDF-", pdf.readBytes().copyOfRange(0, 5).toString(Charsets.US_ASCII))
        }
    }
}
