package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.Account
import com.example.data.local.entities.Expense
import com.example.data.local.entities.ExpenseCategory
import com.example.data.local.entities.JournalEntry
import com.example.data.local.entities.LedgerEntry
import com.example.data.local.entities.Payment
import com.example.data.local.entities.PaymentAllocation
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    @Query("SELECT * FROM accounts WHERE businessId = :businessId ORDER BY code ASC")
    fun observeAccounts(businessId: Long = 1L): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE businessId = :businessId ORDER BY code ASC")
    suspend fun getAllAccountsOnce(businessId: Long = 1L): List<Account>

    @Query("SELECT * FROM accounts WHERE businessId = :businessId AND code = :code LIMIT 1")
    suspend fun getAccountByCode(code: String, businessId: Long = 1L): Account?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Query("UPDATE accounts SET currentBalancePaise = currentBalancePaise + :deltaPaise WHERE id = :accountId")
    suspend fun adjustAccountBalance(accountId: Long, deltaPaise: Long)

    @Query("SELECT * FROM payments WHERE businessId = :businessId ORDER BY paymentDateMillis DESC, id DESC")
    fun observeAllPayments(businessId: Long = 1L): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllPaymentsOnce(businessId: Long = 1L): List<Payment>

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY paymentDateMillis DESC, id DESC")
    fun observePaymentsForCustomer(customerId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE supplierId = :supplierId ORDER BY paymentDateMillis DESC, id DESC")
    fun observePaymentsForSupplier(supplierId: Long): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentAllocation(allocation: PaymentAllocation): Long

    @Query("SELECT pa.* FROM payment_allocations pa INNER JOIN payments p ON pa.paymentId = p.id WHERE p.businessId = :businessId ORDER BY pa.id ASC")
    suspend fun getAllPaymentAllocationsOnce(businessId: Long = 1L): List<PaymentAllocation>

    @Query("SELECT * FROM payment_allocations WHERE invoiceId = :invoiceId")
    fun observeAllocationsForInvoice(invoiceId: Long): Flow<List<PaymentAllocation>>

    @Query("SELECT * FROM expense_categories WHERE businessId = :businessId ORDER BY name ASC")
    fun observeExpenseCategories(businessId: Long = 1L): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getAllExpenseCategoriesOnce(businessId: Long = 1L): List<ExpenseCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseCategory(category: ExpenseCategory): Long

    @Query("SELECT * FROM expenses WHERE businessId = :businessId ORDER BY expenseDateMillis DESC, id DESC")
    fun observeAllExpenses(businessId: Long = 1L): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllExpensesOnce(businessId: Long = 1L): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM journal_entries WHERE businessId = :businessId ORDER BY entryDateMillis DESC, id DESC")
    fun observeJournalEntries(businessId: Long = 1L): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllJournalEntriesOnce(businessId: Long = 1L): List<JournalEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Query("SELECT * FROM ledger_entries WHERE businessId = :businessId ORDER BY entryDateMillis DESC, id DESC")
    fun observeAllLedgerEntries(businessId: Long = 1L): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllLedgerEntriesOnce(businessId: Long = 1L): List<LedgerEntry>

    @Query("SELECT * FROM ledger_entries WHERE partyType = 'CUSTOMER' AND partyId = :customerId ORDER BY entryDateMillis ASC, id ASC")
    fun observeCustomerLedger(customerId: Long): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE partyType = 'SUPPLIER' AND partyId = :supplierId ORDER BY entryDateMillis ASC, id ASC")
    fun observeSupplierLedger(supplierId: Long): Flow<List<LedgerEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntries(entries: List<LedgerEntry>)
}
