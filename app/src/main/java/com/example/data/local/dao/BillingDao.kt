package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.Purchase
import com.example.data.local.entities.PurchaseItem
import com.example.data.local.entities.PurchaseStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BillingDao {
    @Query("SELECT * FROM invoices WHERE businessId = :businessId ORDER BY invoiceDateMillis DESC, id DESC")
    fun observeAllInvoices(businessId: Long = 1L): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllInvoicesOnce(businessId: Long = 1L): List<Invoice>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY invoiceDateMillis DESC, id DESC")
    fun observeInvoicesForCustomer(customerId: Long): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE businessId = :businessId AND status = 'HELD_POS' ORDER BY updatedAt DESC")
    fun observeHeldPosBills(businessId: Long = 1L): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    fun observeInvoiceById(id: Long): Flow<Invoice?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceByIdOnce(id: Long): Invoice?

    @Query("SELECT * FROM invoices WHERE businessId = :businessId AND invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun findInvoiceByNumber(invoiceNumber: String, businessId: Long = 1L): Invoice?

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId ORDER BY id ASC")
    fun observeInvoiceItems(invoiceId: Long): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId ORDER BY id ASC")
    suspend fun getInvoiceItemsOnce(invoiceId: Long): List<InvoiceItem>

    @Query("SELECT ii.* FROM invoice_items ii INNER JOIN invoices i ON ii.invoiceId = i.id WHERE i.businessId = :businessId ORDER BY ii.id ASC")
    suspend fun getAllInvoiceItemsOnce(businessId: Long = 1L): List<InvoiceItem>

    @Query("SELECT ii.* FROM invoice_items ii INNER JOIN invoices i ON ii.invoiceId = i.id WHERE i.businessId = :businessId ORDER BY ii.id ASC")
    fun observeAllInvoiceItems(businessId: Long = 1L): Flow<List<InvoiceItem>>

    @Query("SELECT COUNT(*) FROM invoices WHERE businessId = :businessId AND invoiceNumber = :invoiceNumber")
    suspend fun countByInvoiceNumber(invoiceNumber: String, businessId: Long = 1L): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItems(items: List<InvoiceItem>)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceItems(invoiceId: Long)

    @Query("DELETE FROM invoices WHERE id = :invoiceId AND (status = 'DRAFT' OR status = 'HELD_POS')")
    suspend fun deleteDraftInvoice(invoiceId: Long)

    @Query("UPDATE invoices SET paidAmountPaise = :paidPaise, balanceDuePaise = :balanceDuePaise, status = :status, updatedAt = :now WHERE id = :invoiceId")
    suspend fun updateInvoicePaymentState(
        invoiceId: Long,
        paidPaise: Long,
        balanceDuePaise: Long,
        status: InvoiceStatus,
        now: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM purchases WHERE businessId = :businessId ORDER BY purchaseDateMillis DESC, id DESC")
    fun observeAllPurchases(businessId: Long = 1L): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllPurchasesOnce(businessId: Long = 1L): List<Purchase>

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY purchaseDateMillis DESC, id DESC")
    fun observePurchasesForSupplier(supplierId: Long): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    fun observePurchaseById(id: Long): Flow<Purchase?>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseByIdOnce(id: Long): Purchase?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id ASC")
    fun observePurchaseItems(purchaseId: Long): Flow<List<PurchaseItem>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id ASC")
    suspend fun getPurchaseItemsOnce(purchaseId: Long): List<PurchaseItem>

    @Query("SELECT pi.* FROM purchase_items pi INNER JOIN purchases p ON pi.purchaseId = p.id WHERE p.businessId = :businessId ORDER BY pi.id ASC")
    suspend fun getAllPurchaseItemsOnce(businessId: Long = 1L): List<PurchaseItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Update
    suspend fun updatePurchase(purchase: Purchase)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItem>)

    @Query("UPDATE purchases SET paidAmountPaise = :paidPaise, balanceDuePaise = :balanceDuePaise, status = :status, updatedAt = :now WHERE id = :purchaseId")
    suspend fun updatePurchasePaymentState(
        purchaseId: Long,
        paidPaise: Long,
        balanceDuePaise: Long,
        status: PurchaseStatus,
        now: Long = System.currentTimeMillis()
    )
}
