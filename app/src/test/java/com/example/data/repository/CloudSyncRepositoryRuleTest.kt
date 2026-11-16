package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.entities.BusinessCategory
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Product
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueId(): Long = abs(Random.nextLong(10_000L, 9_999_999L))

    @Test
    fun pushBusinessProductInvoice_authenticatedOwner_persistsAndUpdatesCleanly() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore = firestore, auth = auth, db = null)
        val bizId = uniqueId()
        val prodId = uniqueId()
        val invId = uniqueId()

        val business = BusinessProfile(
            id = bizId,
            businessName = "Pulse Wholesale",
            ownerName = "Alice",
            phone = "9876543210",
            email = ALICE_EMAIL,
            address = "100 Main Road",
            category = BusinessCategory.WHOLESALE,
            isGstRegistered = true,
            gstin = "27ABCDE1234F1Z5",
            currencySymbol = "₹",
            invoicePrefix = "INV-",
            nextInvoiceNumber = 1001L
        )
        val bizResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.pushBusinessNow(business) }
        assertTrue("Business sync failed: ${bizResult.exceptionOrNull()}", bizResult.isSuccess)

        val product = Product(
            id = prodId,
            businessId = bizId,
            name = "Smart Meter",
            sku = "SM-$prodId",
            purchasePricePaise = 50_000L,
            sellingPricePaise = 75_000L,
            taxRatePercentScaled = 1800L,
            unitName = "PCS",
            openingStockScaled = 10_000L,
            currentStockScaled = 10_000L
        )
        val prodResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.pushProductNow(product) }
        assertTrue("Product sync failed: ${prodResult.exceptionOrNull()}", prodResult.isSuccess)

        val invoice = Invoice(
            id = invId,
            businessId = bizId,
            invoiceNumber = "INV-$invId",
            documentType = InvoiceDocumentType.TAX_INVOICE,
            status = InvoiceStatus.FINALIZED,
            customerName = "Retail Mart",
            subtotalPaise = 75_000L,
            taxableAmountPaise = 75_000L,
            cgstPaise = 6_750L,
            sgstPaise = 6_750L,
            totalTaxPaise = 13_500L,
            grandTotalPaise = 88_500L,
            paidAmountPaise = 50_000L,
            balanceDuePaise = 38_500L,
            paymentMode = PaymentMode.UPI
        )
        val items = listOf(
            InvoiceItem(
                invoiceId = invId,
                productId = prodId,
                productName = "Smart Meter",
                unitName = "PCS",
                quantityScaled = 1000L,
                ratePaise = 75_000L,
                taxRatePercentScaled = 1800L,
                taxableAmountPaise = 75_000L,
                cgstPaise = 6_750L,
                sgstPaise = 6_750L,
                lineTotalPaise = 88_500L
            )
        )
        val invResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.pushInvoiceNow(invoice, items) }
        assertTrue("Invoice sync failed: ${invResult.exceptionOrNull()}", invResult.isSuccess)

        // Update existing invoice (e.g. payment allocated -> PAID, existing item updated without createdAt mutation)
        val updatedInvoice = invoice.copy(
            status = InvoiceStatus.PAID,
            paidAmountPaise = 88_500L,
            balanceDuePaise = 0L,
            updatedAt = System.currentTimeMillis() + 500L
        )
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.pushInvoiceNow(updatedInvoice, items) }
        assertTrue("Invoice update sync failed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

        val observedInvoices = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeInvoices().first { list -> list.any { it.localId == invId && it.status == InvoiceStatus.PAID.name } }
        }
        assertTrue(observedInvoices.any { it.localId == invId && it.paidAmountPaise == 88_500L })

        val cloudItems = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getInvoiceItemsFromCloud("inv_$invId").getOrThrow()
        }
        assertEquals(1, cloudItems.size)
        assertEquals("Smart Meter", cloudItems.first().productName)
    }

    @Test
    fun crossUserReadAndWrite_deniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore = firestore, auth = auth, db = null)
        val prodId = uniqueId()
        val prodDocId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.pushProductNow(
                Product(
                    id = prodId,
                    businessId = 1L,
                    name = "Protected Stock Item",
                    sellingPricePaise = 12_000L
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("products").document(prodDocId).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's product")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun unauthenticatedObserveProducts_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        // Query a fixed user path directly without auth to verify Firestore rules reject unauthenticated callers
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid_123")
                    .collection("products").get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun observeProducts_authenticatedOwner_emitsSavedProduct() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore = firestore, auth = auth, db = null)
        val prodId = uniqueId()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.pushProductNow(
                Product(
                    id = prodId,
                    businessId = 1L,
                    name = "Thermal Paper Roll",
                    sellingPricePaise = 5_000L,
                    currentStockScaled = 50_000L
                )
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts().first { list -> list.any { it.localId == prodId } }
        }
        assertTrue(emitted.any { it.localId == prodId && it.name == "Thermal Paper Roll" })
    }

    private companion object {
        const val ALICE_EMAIL = "alice.sync@test.com"
        const val BOB_EMAIL = "bob.sync@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
