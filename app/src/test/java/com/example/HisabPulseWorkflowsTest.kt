package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AppThemeMode
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Customer
import com.example.data.local.entities.Expense
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Product
import com.example.data.local.entities.PurchaseDocumentType
import com.example.data.local.entities.Supplier
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.AccountingRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.PartyRepository
import com.example.data.repository.PurchaseLineDraft
import com.example.data.repository.SaveInvoiceRequest
import com.example.data.repository.SavePurchaseRequest
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
class HisabPulseWorkflowsTest {

    private lateinit var context: Context
    private lateinit var db: HisabPulseDatabase
    private lateinit var prefs: AppPreferencesRepository
    private lateinit var businessRepo: BusinessRepository
    private lateinit var partyRepo: PartyRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var billingRepo: BillingRepository
    private lateinit var accountingRepo: AccountingRepository
    private lateinit var backupRepo: BackupRepository

    private val testDbName = "hisabpulse_test_workflows.db"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        db = buildDb()
        HisabPulseDatabase.ensureSystemSeeded(db)
        prefs = AppPreferencesRepository(context)
        wireRepos()
    }

    private fun buildDb(): HisabPulseDatabase =
        Room.databaseBuilder(context, HisabPulseDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()

    private fun wireRepos() {
        businessRepo = BusinessRepository(db, prefs)
        partyRepo = PartyRepository(db)
        inventoryRepo = InventoryRepository(db)
        billingRepo = BillingRepository(db)
        accountingRepo = AccountingRepository(db)
        backupRepo = BackupRepository(context, db, prefs)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        context.deleteDatabase(testDbName)
    }

    @Test
    fun `fresh database has zero fabricated transactions or parties`() = runBlocking {
        assertTrue(partyRepo.customers.first().isEmpty())
        assertTrue(partyRepo.suppliers.first().isEmpty())
        assertTrue(inventoryRepo.activeProducts.first().isEmpty())
        assertTrue(billingRepo.allInvoices.first().isEmpty())
        assertTrue(billingRepo.allPurchases.first().isEmpty())
        assertTrue(accountingRepo.expenses.first().isEmpty())
        assertTrue(accountingRepo.payments.first().isEmpty())
        // Structural metadata IS seeded
        assertTrue(accountingRepo.accounts.first().isNotEmpty())
        assertTrue(businessRepo.taxConfigurations.first().isNotEmpty())
        assertTrue(businessRepo.invoiceTemplates.first().size == 9)
    }

    @Test
    fun `all 18 business accounting inventory draft backup pdf theme and offline workflows`() = runBlocking {
        // ── Workflow 1: Business Profile & Onboarding Persistence ──
        businessRepo.saveBusinessProfile(
            BusinessProfile(
                businessName = "Nakshatra Traders",
                ownerName = "Aarav Mehta",
                phone = "9876543210",
                state = "Maharashtra",
                gstin = "27AAPFU0939F1ZV",
                upiId = "nakshatra@upi",
                invoicePrefix = "NT-"
            ),
            completeOnboarding = true
        )
        val savedProfile = businessRepo.businessProfile.first()
        assertNotNull(savedProfile)
        assertEquals("Nakshatra Traders", savedProfile!!.businessName)
        assertTrue(prefs.onboardingCompleted.first())

        // ── Workflow 2: Add Product with Opening Stock (Verify StockMovement & Accounting) ──
        val productId = inventoryRepo.saveProduct(
            Product(
                name = "Wireless Barcode Scanner",
                productCode = "WBS-01",
                barcode = "8901234567890",
                hsnSacCode = "8471",
                unitName = "PCS",
                purchasePricePaise = 1000_00L, // ₹1,000.00
                sellingPricePaise = 1500_00L,  // ₹1,500.00
                taxRatePercentScaled = 1800L,  // 18%
                openingStockScaled = 10_00L,   // 10.00 PCS -> Opening value ₹10,000
                minStockScaled = 3_00L
            )
        )
        val loadedProduct = inventoryRepo.observeProduct(productId).first()!!
        assertEquals(10_00L, loadedProduct.currentStockScaled)
        assertEquals(1, inventoryRepo.observeProductMovements(productId).first().size)

        // ── Workflow 3: Create Customer (with opening balance) & Supplier ──
        val customerId = partyRepo.saveCustomer(
            Customer(
                name = "Rohan Electronics",
                phone = "9820011223",
                gstin = "27BBBBB0000B1Z5",
                state = "Maharashtra",
                openingBalancePaise = 500_00L // ₹500 opening receivable
            )
        )
        val supplierId = partyRepo.saveSupplier(
            Supplier(
                name = "Apex Imports Pvt Ltd",
                phone = "9811122334",
                gstin = "27CCCCC1111C1Z9"
            )
        )
        assertEquals(500_00L, partyRepo.observeCustomer(customerId).first()!!.currentBalancePaise)

        // ── Workflow 8 (Part A): Create Draft Bill -> Edit Draft -> Verify Zero Impact Before Finalizing ──
        val draftId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                documentType = InvoiceDocumentType.TAX_INVOICE,
                saveAsDraft = true,
                customerId = customerId,
                customerName = "Rohan Electronics",
                items = listOf(
                    InvoiceLineDraft(
                        productId = productId,
                        productName = "Wireless Barcode Scanner",
                        unitName = "PCS",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("1500.00"),
                        purchaseCost = BigDecimal("1000.00"),
                        taxRatePercent = BigDecimal("18")
                    )
                )
            )
        ).getOrThrow()

        // Editing existing draft: change qty to 2 and add a service line
        billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                existingInvoiceId = draftId,
                documentType = InvoiceDocumentType.TAX_INVOICE,
                saveAsDraft = true,
                customerId = customerId,
                customerName = "Rohan Electronics",
                items = listOf(
                    InvoiceLineDraft(
                        productId = productId,
                        productName = "Wireless Barcode Scanner",
                        unitName = "PCS",
                        quantity = BigDecimal("2"),
                        rate = BigDecimal("1500.00"),
                        purchaseCost = BigDecimal("1000.00"),
                        taxRatePercent = BigDecimal("18")
                    ),
                    InvoiceLineDraft(
                        productId = null,
                        productName = "Setup & Installation",
                        unitName = "HR",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("500.00"),
                        taxRatePercent = BigDecimal("18")
                    )
                )
            )
        ).getOrThrow()

        val draftCheck = billingRepo.getInvoiceWithItemsOnce(draftId)!!
        assertEquals(InvoiceStatus.DRAFT, draftCheck.first.status)
        assertEquals(2, draftCheck.second.size)
        // Stock & customer balance remain untouched while in DRAFT
        assertEquals(10_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        assertEquals(500_00L, partyRepo.observeCustomer(customerId).first()!!.currentBalancePaise)

        // ── Workflows 4-7 + Workflow 8 (Part B): Finalize Draft Invoice -> Partial Payment -> Balance, Stock & Ledger ──
        // Taxable: 2*1500 + 500 = 3500. GST 18% = 630 (315 CGST + 315 SGST). Grand Total = ₹4,130.00.
        // Pay ₹1,130.00 at billing time -> Balance due = ₹3,000.00.
        billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                existingInvoiceId = draftId,
                documentType = InvoiceDocumentType.TAX_INVOICE,
                saveAsDraft = false,
                customerId = customerId,
                customerName = "Rohan Electronics",
                paidAmount = BigDecimal("1130.00"),
                paymentMode = PaymentMode.UPI,
                items = listOf(
                    InvoiceLineDraft(
                        productId = productId,
                        productName = "Wireless Barcode Scanner",
                        unitName = "PCS",
                        quantity = BigDecimal("2"),
                        rate = BigDecimal("1500.00"),
                        purchaseCost = BigDecimal("1000.00"),
                        taxRatePercent = BigDecimal("18")
                    ),
                    InvoiceLineDraft(
                        productId = null,
                        productName = "Setup & Installation",
                        unitName = "HR",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("500.00"),
                        taxRatePercent = BigDecimal("18")
                    )
                )
            )
        ).getOrThrow()

        val finalized = billingRepo.getInvoiceWithItemsOnce(draftId)!!.first
        assertEquals(InvoiceStatus.PARTIAL, finalized.status)
        assertEquals(4130_00L, finalized.grandTotalPaise)
        assertEquals(3000_00L, finalized.balanceDuePaise)
        // Stock reduced from 10 -> 8
        assertEquals(8_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        // Customer outstanding: 500 opening + 3000 invoice balance = 3500
        assertEquals(3500_00L, partyRepo.observeCustomer(customerId).first()!!.currentBalancePaise)

        // Finalized invoice CANNOT be overwritten
        val overwriteAttempt = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                existingInvoiceId = draftId,
                items = listOf(InvoiceLineDraft(productName = "Hack", rate = BigDecimal.ONE))
            )
        )
        assertTrue(overwriteAttempt.isFailure)

        // Record subsequent customer payment of ₹2,000.00
        accountingRepo.recordCustomerPayment(
            customerId = customerId,
            amountPaise = 2000_00L,
            paymentMode = PaymentMode.CASH,
            invoiceIdToAllocate = draftId
        ).getOrThrow()

        assertEquals(1500_00L, partyRepo.observeCustomer(customerId).first()!!.currentBalancePaise)
        val invAfterPay = billingRepo.getInvoiceWithItemsOnce(draftId)!!.first
        assertEquals(3130_00L, invAfterPay.paidAmountPaise)
        assertEquals(1000_00L, invAfterPay.balanceDuePaise)

        // ── Workflows 9-10: Purchase Increases Stock & Supplier Payment ──
        val purchaseId = billingRepo.savePurchase(
            SavePurchaseRequest(
                supplierId = supplierId,
                supplierName = "Apex Imports Pvt Ltd",
                documentType = PurchaseDocumentType.PURCHASE_INVOICE,
                paidAmount = BigDecimal("1000.00"),
                paymentMode = PaymentMode.BANK_TRANSFER,
                items = listOf(
                    PurchaseLineDraft(
                        productId = productId,
                        productName = "Wireless Barcode Scanner",
                        unitName = "PCS",
                        quantity = BigDecimal("5"),
                        rate = BigDecimal("1000.00"),
                        taxRatePercent = BigDecimal("18")
                    )
                )
            )
        ).getOrThrow()
        assertNotNull(purchaseId)
        // Stock increased 8 -> 13
        assertEquals(13_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        // Purchase total = 5000 + 900 GST = 5900; paid 1000 -> supplier balance = 4900
        assertEquals(4900_00L, partyRepo.observeSupplier(supplierId).first()!!.currentBalancePaise)

        accountingRepo.recordSupplierPayment(
            supplierId = supplierId,
            amountPaise = 1900_00L,
            paymentMode = PaymentMode.CASH,
            purchaseIdToAllocate = purchaseId
        ).getOrThrow()
        assertEquals(3000_00L, partyRepo.observeSupplier(supplierId).first()!!.currentBalancePaise)

        // ── Workflow 11: Add Cash Expense & Verify Cash Account Reduction ──
        val cashBeforeExpense = accountingRepo.cashBalancePaise.first()
        accountingRepo.saveExpense(
            Expense(
                categoryName = "Rent",
                amountPaise = 400_00L,
                paymentMode = PaymentMode.CASH,
                description = "Shop counter rent"
            )
        ).getOrThrow()
        assertEquals(cashBeforeExpense - 400_00L, accountingRepo.cashBalancePaise.first())

        // ── Auditable Cancellation & Reversal Test on Second Invoice ──
        val invToCancelId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                documentType = InvoiceDocumentType.TAX_INVOICE,
                customerId = customerId,
                customerName = "Rohan Electronics",
                paidAmount = BigDecimal.ZERO,
                items = listOf(
                    InvoiceLineDraft(
                        productId = productId,
                        productName = "Wireless Barcode Scanner",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("1500.00"),
                        purchaseCost = BigDecimal("1000.00"),
                        taxRatePercent = BigDecimal("18")
                    )
                )
            )
        ).getOrThrow()
        assertEquals(12_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        billingRepo.cancelFinalizedInvoice(invToCancelId, "Customer cancelled order").getOrThrow()
        assertEquals(InvoiceStatus.CANCELLED, billingRepo.getInvoiceWithItemsOnce(invToCancelId)!!.first.status)
        assertEquals(13_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        assertEquals(1500_00L, partyRepo.observeCustomer(customerId).first()!!.currentBalancePaise)

        // ── Workflow 12: Trial Balance, P&L, Balance Sheet & Double-Entry Equality ──
        val allAccounts = accountingRepo.accounts.first()
        val allLedger = accountingRepo.ledgerEntries.first()
        val journals = accountingRepo.journalEntries.first()
        assertTrue(journals.isNotEmpty())
        // Every individual journal is balanced
        journals.forEach { assertEquals(it.totalDebitPaise, it.totalCreditPaise) }

        val trialRows = accountingRepo.buildTrialBalance(allAccounts, allLedger)
        val totalDebits = trialRows.sumOf { it.totalDebitPaise }
        val totalCredits = trialRows.sumOf { it.totalCreditPaise }
        assertTrue(totalDebits > 0L)
        assertEquals(totalDebits, totalCredits)

        val pnl = accountingRepo.buildProfitAndLoss(allAccounts)
        // Sales revenue = 3500, COGS = 2*1000 = 2000, Operating Expense = 400 -> Net Profit = 1100
        assertEquals(3500_00L, pnl.salesRevenuePaise)
        assertEquals(2000_00L, pnl.cogsPaise)
        assertEquals(400_00L, pnl.operatingExpensesPaise)
        assertEquals(1100_00L, pnl.netProfitPaise)

        val bs = accountingRepo.buildBalanceSheet(allAccounts)
        assertEquals(bs.totalAssetsPaise, bs.totalLiabilitiesAndEquityPaise)

        // ── Workflow 13: PDF Invoice & CSV Report Generation ──
        val tpl = businessRepo.selectedTemplate.first()
        val (invEntity, invItems) = billingRepo.getInvoiceWithItemsOnce(draftId)!!
        val pdfFile = PdfGenerator.generateInvoicePdf(context, savedProfile, invEntity, invItems, tpl)
        assertTrue(pdfFile.exists())
        val pdfHeader = pdfFile.readBytes().copyOfRange(0, 5).toString(Charsets.US_ASCII)
        assertEquals("%PDF-", pdfHeader)

        val csvFile = PdfGenerator.exportCsv(
            context = context,
            fileName = "test_sales.csv",
            headers = listOf("Invoice", "Customer", "Total"),
            rows = listOf(listOf(invEntity.invoiceNumber, invEntity.customerName, Money.formatPlain(Money.fromPaise(invEntity.grandTotalPaise))))
        )
        assertTrue(csvFile.exists())
        assertTrue(csvFile.readText().contains(invEntity.invoiceNumber))

        // ── Workflows 14-15: Encrypted Backup Export, Tamper Detection, Wipe & Atomic Restore ──
        val backupBytes = backupRepo.createBackupBytes(encrypt = true, passphrase = "OwnerBackup#2026")
        // Wrong passphrase must fail
        assertTrue(runCatching { backupRepo.decodeAndValidateBackupBytes(backupBytes, "WrongKey") }.isFailure)
        // Tampered ciphertext must fail GCM auth
        val tampered = backupBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0xFF).toByte() }
        assertTrue(runCatching { backupRepo.decodeAndValidateBackupBytes(tampered, "OwnerBackup#2026") }.isFailure)

        val decodedSnapshot = backupRepo.decodeAndValidateBackupBytes(backupBytes, "OwnerBackup#2026")
        assertEquals("Nakshatra Traders", decodedSnapshot.businessProfile?.businessName)

        // Simulate clearing local data then restoring
        db.clearAllTables()
        assertTrue(partyRepo.customers.first().isEmpty())
        backupRepo.restoreFromSnapshot(decodedSnapshot).getOrThrow()

        assertEquals("Nakshatra Traders", businessRepo.businessProfile.first()!!.businessName)
        assertEquals(13_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        assertEquals(1500_00L, partyRepo.observeCustomer(customerId).first()!!.currentBalancePaise)
        assertEquals(3000_00L, partyRepo.observeSupplier(supplierId).first()!!.currentBalancePaise)

        // ── Workflow 17: Theme Switching Across Light, Dark, Orange ──
        businessRepo.setTheme(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, prefs.themeMode.first())
        businessRepo.setTheme(AppThemeMode.ORANGE)
        assertEquals(AppThemeMode.ORANGE, prefs.themeMode.first())
        businessRepo.setTheme(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode.first())

        // ── Workflow 16: Close & Reopen Database Verify Persistence ──
        db.close()
        db = buildDb()
        wireRepos()
        assertEquals("Nakshatra Traders", businessRepo.businessProfile.first()!!.businessName)
        assertEquals(13_00L, inventoryRepo.observeProduct(productId).first()!!.currentStockScaled)
        assertEquals(totalDebits, accountingRepo.ledgerEntries.first().sumOf { it.debitPaise })

        // ── Workflow 18: Verify Permissions Architecture (INTERNET enabled for Firebase Auth/Profile, zero tracking) ──
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertTrue("Firebase Auth requires INTERNET permission", perms.contains("android.permission.INTERNET"))
    }
}
