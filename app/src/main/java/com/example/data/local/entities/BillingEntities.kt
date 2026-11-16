package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("businessId"),
        Index(value = ["businessId", "invoiceNumber"], unique = true),
        Index("customerId"),
        Index("invoiceDateMillis"),
        Index("status")
    ]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val invoiceNumber: String,
    val documentType: InvoiceDocumentType = InvoiceDocumentType.TAX_INVOICE,
    val status: InvoiceStatus = InvoiceStatus.FINALIZED,
    val invoiceDateMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val customerId: Long? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerGstin: String = "",
    val isInterState: Boolean = false,
    val subtotalPaise: Long = 0L,
    val itemDiscountPaise: Long = 0L,
    val invoiceDiscountPercentScaled: Long = 0L,
    val invoiceDiscountFlatPaise: Long = 0L,
    val taxableAmountPaise: Long = 0L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val totalTaxPaise: Long = 0L,
    val additionalChargesPaise: Long = 0L,
    val shippingChargesPaise: Long = 0L,
    val roundOffPaise: Long = 0L,
    val grandTotalPaise: Long = 0L,
    val paidAmountPaise: Long = 0L,
    val balanceDuePaise: Long = 0L,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val paymentReference: String = "",
    val loyaltyPointsEarned: Long = 0L,
    val loyaltyPointsRedeemed: Long = 0L,
    val loyaltyDiscountPaise: Long = 0L,
    val tableNumber: String = "",
    val kotStatus: String = "",
    val linkedOriginalInvoiceId: Long? = null,
    val notes: String = "",
    val termsAndConditions: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("invoiceId"), Index("productId")]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long? = null,
    val batchId: Long? = null,
    val productName: String,
    val productCode: String = "",
    val hsnSacCode: String = "",
    val unitName: String = "PCS",
    val quantityScaled: Long = 100L, // 100 = 1.00
    val ratePaise: Long = 0L,
    val purchaseCostPaise: Long = 0L,
    val discountPercentScaled: Long = 0L,
    val discountAmountPaise: Long = 0L,
    val taxRatePercentScaled: Long = 0L,
    val isTaxInclusive: Boolean = false,
    val taxableAmountPaise: Long = 0L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val lineTotalPaise: Long = 0L,
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,
    val serialNumber: String = ""
)

@Serializable
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("businessId"),
        Index(value = ["businessId", "purchaseNumber"], unique = true),
        Index("supplierId"),
        Index("purchaseDateMillis")
    ]
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val purchaseNumber: String,
    val supplierBillNumber: String = "",
    val documentType: PurchaseDocumentType = PurchaseDocumentType.PURCHASE_INVOICE,
    val status: PurchaseStatus = PurchaseStatus.FINALIZED,
    val purchaseDateMillis: Long = System.currentTimeMillis(),
    val supplierId: Long? = null,
    val supplierName: String = "Cash Supplier",
    val supplierGstin: String = "",
    val isInterState: Boolean = false,
    val subtotalPaise: Long = 0L,
    val discountPaise: Long = 0L,
    val taxableAmountPaise: Long = 0L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val totalTaxPaise: Long = 0L,
    val additionalChargesPaise: Long = 0L,
    val grandTotalPaise: Long = 0L,
    val paidAmountPaise: Long = 0L,
    val balanceDuePaise: Long = 0L,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val linkedOriginalPurchaseId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("purchaseId"), Index("productId")]
)
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long? = null,
    val productName: String,
    val hsnSacCode: String = "",
    val unitName: String = "PCS",
    val quantityScaled: Long = 100L,
    val ratePaise: Long = 0L,
    val discountPercentScaled: Long = 0L,
    val taxRatePercentScaled: Long = 0L,
    val taxableAmountPaise: Long = 0L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val lineTotalPaise: Long = 0L,
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null
)
