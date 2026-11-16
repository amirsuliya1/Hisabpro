package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "accounts",
    indices = [Index("businessId"), Index(value = ["businessId", "code"], unique = true)]
)
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val code: String,
    val name: String,
    val accountType: AccountType,
    val isBankAccount: Boolean = false,
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val upiId: String = "",
    val openingBalancePaise: Long = 0L,
    val currentBalancePaise: Long = 0L,
    val isSystemAccount: Boolean = false
)

@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("businessId"), Index("customerId"), Index("supplierId"), Index("paymentDateMillis")]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val receiptNumber: String,
    val direction: PaymentDirection,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val amountPaise: Long,
    val paymentDateMillis: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val supplierId: Long? = null,
    val partyName: String = "",
    val accountId: Long? = null,
    val referenceNumber: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = Payment::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paymentId"), Index("invoiceId"), Index("purchaseId")]
)
data class PaymentAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val invoiceId: Long? = null,
    val purchaseId: Long? = null,
    val allocatedAmountPaise: Long
)

@Serializable
@Entity(
    tableName = "expense_categories",
    indices = [Index(value = ["businessId", "name"], unique = true)]
)
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val isSystemDefault: Boolean = false
)

@Serializable
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("businessId"), Index("categoryId"), Index("expenseDateMillis")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val categoryId: Long? = null,
    val categoryName: String,
    val amountPaise: Long,
    val expenseDateMillis: Long = System.currentTimeMillis(),
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val accountId: Long? = null,
    val description: String = "",
    val receiptUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "journal_entries",
    indices = [Index("businessId"), Index("entryDateMillis"), Index("referenceType", "referenceId")]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val entryNumber: String,
    val entryDateMillis: Long = System.currentTimeMillis(),
    val description: String,
    val referenceType: String = "", // INVOICE, PURCHASE, PAYMENT, EXPENSE, OPENING, ADJUSTMENT
    val referenceId: Long? = null,
    val totalDebitPaise: Long,
    val totalCreditPaise: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntry::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("journalEntryId"), Index("accountId"), Index("entryDateMillis")]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val journalEntryId: Long,
    val accountId: Long,
    val accountName: String,
    val entryDateMillis: Long = System.currentTimeMillis(),
    val debitPaise: Long = 0L,
    val creditPaise: Long = 0L,
    val partyType: String = "", // CUSTOMER, SUPPLIER, NONE
    val partyId: Long? = null,
    val narration: String = ""
)
