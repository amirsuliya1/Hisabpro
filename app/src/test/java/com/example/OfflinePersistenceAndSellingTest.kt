package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Product
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.SaveInvoiceRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflinePersistenceAndSellingTest {

    private lateinit var context: Context
    private lateinit var db: HisabPulseDatabase
    private lateinit var prefs: AppPreferencesRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var billingRepo: BillingRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HisabPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = AppPreferencesRepository(context)
        prefs.clearSessionCacheSync()
        inventoryRepo = InventoryRepository(db)
        billingRepo = BillingRepository(db)

        runBlocking {
            HisabPulseDatabase.ensureSystemSeeded(db)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fastSessionCache_preventsReopenLoadingAndReset() = runBlocking {
        assertFalse(prefs.hasCompletedSetupForUidSync("uid_123"))
        assertFalse(prefs.isOnboardingCompletedSync())

        prefs.markSetupCompleteForUidSync("uid_123")
        prefs.setOnboardingCompleted(true)
        prefs.setPin("4321")

        // Synchronous frame-0 reads survive immediately without DataStore I/O delay
        assertTrue(prefs.hasCompletedSetupForUidSync("uid_123"))
        assertFalse(prefs.hasCompletedSetupForUidSync("uid_other"))
        assertTrue(prefs.isOnboardingCompletedSync())
        assertTrue(prefs.isPinEnabledSync())

        prefs.disablePin()
        assertFalse(prefs.isPinEnabledSync())

        prefs.clearSessionCacheSync()
        assertFalse(prefs.hasCompletedSetupForUidSync("uid_123"))
    }

    @Test
    fun cancelSalesReturn_reversesStockDownwardAccurately() = runBlocking {
        val prodId = inventoryRepo.saveProduct(
            Product(
                businessId = 1L,
                name = "Wireless Mouse",
                sellingPricePaise = 50_000L,
                purchasePricePaise = 30_000L,
                taxRatePercentScaled = 1800L,
                openingStockScaled = Money.safeToPaise(BigDecimal("10")),
                currentStockScaled = Money.safeToPaise(BigDecimal("10"))
            )
        )

        // Finalize a regular sale of 2 units -> stock becomes 8
        val saleId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                documentType = InvoiceDocumentType.TAX_INVOICE,
                items = listOf(
                    InvoiceLineDraft(
                        productId = prodId,
                        productName = "Wireless Mouse",
                        quantity = BigDecimal("2"),
                        rate = BigDecimal("500"),
                        purchaseCost = BigDecimal("300"),
                        taxRatePercent = BigDecimal("18")
                    )
                ),
                paidAmount = BigDecimal("1180"),
                paymentMode = PaymentMode.CASH
            )
        ).getOrThrow()

        assertEquals(Money.safeToPaise(BigDecimal("8")), db.inventoryDao().getProductByIdOnce(prodId)!!.currentStockScaled)

        // Create Sales Return of 1 unit -> stock becomes 9
        val returnId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                documentType = InvoiceDocumentType.SALES_RETURN,
                linkedOriginalInvoiceId = saleId,
                items = listOf(
                    InvoiceLineDraft(
                        productId = prodId,
                        productName = "Wireless Mouse",
                        quantity = BigDecimal("1"),
                        rate = BigDecimal("500"),
                        purchaseCost = BigDecimal("300"),
                        taxRatePercent = BigDecimal("18")
                    )
                ),
                paidAmount = BigDecimal.ZERO
            )
        ).getOrThrow()

        assertEquals(Money.safeToPaise(BigDecimal("9")), db.inventoryDao().getProductByIdOnce(prodId)!!.currentStockScaled)

        // Cancel that Sales Return -> stock must reverse back down from 9 to 8 (not 10!)
        val cancelRes = billingRepo.cancelFinalizedInvoice(returnId, "Test cancel")
        assertTrue(cancelRes.isSuccess)
        assertEquals(InvoiceStatus.CANCELLED, db.billingDao().getInvoiceByIdOnce(returnId)!!.status)
        assertEquals(Money.safeToPaise(BigDecimal("8")), db.inventoryDao().getProductByIdOnce(prodId)!!.currentStockScaled)
    }
}
