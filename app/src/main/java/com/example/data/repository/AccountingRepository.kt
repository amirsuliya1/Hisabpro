package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.Account
import com.example.data.local.entities.AccountType
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.Expense
import com.example.data.local.entities.ExpenseCategory
import com.example.data.local.entities.InvoiceAuditLog
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.JournalEntry
import com.example.data.local.entities.LedgerEntry
import com.example.data.local.entities.Payment
import com.example.data.local.entities.PaymentAllocation
import com.example.data.local.entities.PaymentDirection
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PurchaseStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class TrialBalanceRow(
    val account: Account,
    val totalDebitPaise: Long,
    val totalCreditPaise: Long
)

data class ProfitAndLossSummary(
    val salesRevenuePaise: Long,
    val otherIncomePaise: Long,
    val cogsPaise: Long,
    val operatingExpensesPaise: Long,
    val grossProfitPaise: Long,
    val netProfitPaise: Long
)

data class BalanceSheetSummary(
    val assetAccounts: List<Account>,
    val liabilityAccounts: List<Account>,
    val equityAccounts: List<Account>,
    val netProfitPaise: Long,
    val totalAssetsPaise: Long,
    val totalLiabilitiesAndEquityPaise: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingRepository(
    private val db: HisabPulseDatabase,
    private val cloudSyncRepository: CloudSyncRepository? = null
) {
    private val accountingDao = db.accountingDao()
    private val partyDao = db.partyDao()
    private val billingDao = db.billingDao()
    private val coreDao = db.coreDao()
    private val advancedDao = db.advancedDao()
    private val ledgerService = DoubleEntryLedgerService(accountingDao)

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val accounts: Flow<List<Account>> = activeBizFlow.flatMapLatest { accountingDao.observeAccounts(it) }
    val payments: Flow<List<Payment>> = activeBizFlow.flatMapLatest { accountingDao.observeAllPayments(it) }
    val expenses: Flow<List<Expense>> = activeBizFlow.flatMapLatest { accountingDao.observeAllExpenses(it) }
    val expenseCategories: Flow<List<ExpenseCategory>> = activeBizFlow.flatMapLatest { accountingDao.observeExpenseCategories(it) }
    val journalEntries: Flow<List<JournalEntry>> = activeBizFlow.flatMapLatest { accountingDao.observeJournalEntries(it) }
    val ledgerEntries: Flow<List<LedgerEntry>> = activeBizFlow.flatMapLatest { accountingDao.observeAllLedgerEntries(it) }

    val cashBalancePaise: Flow<Long> = accounts.map { list ->
        list.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.CASH }?.currentBalancePaise ?: 0L
    }

    val bankBalancePaise: Flow<Long> = accounts.map { list ->
        list.filter { it.isBankAccount || it.code == HisabPulseDatabase.Companion.AccountCodes.BANK }
            .sumOf { it.currentBalancePaise }
    }

    fun observeCustomerPayments(customerId: Long): Flow<List<Payment>> =
        accountingDao.observePaymentsForCustomer(customerId)

    fun observeSupplierPayments(supplierId: Long): Flow<List<Payment>> =
        accountingDao.observePaymentsForSupplier(supplierId)

    suspend fun recordCustomerPayment(
        customerId: Long,
        amountPaise: Long,
        paymentMode: PaymentMode,
        accountId: Long? = null,
        invoiceIdToAllocate: Long? = null,
        isAdvance: Boolean = false,
        dateMillis: Long = System.currentTimeMillis(),
        referenceNumber: String = "",
        notes: String = ""
    ): Result<Long> = runCatching {
        require(amountPaise > 0L) { "Payment amount must be positive" }
        db.withTransaction {
            val customer = requireNotNull(partyDao.getCustomerByIdOnce(customerId)) { "Customer not found" }
            val bizId = customer.businessId
            val targetAccount = ledgerService.resolveAccountForPaymentMode(paymentMode, accountId, bizId)
            val direction = if (isAdvance) PaymentDirection.CUSTOMER_ADVANCE else PaymentDirection.RECEIVED
            val receiptNo = "RCP-${System.currentTimeMillis() % 1000000}"

            val paymentId = accountingDao.insertPayment(
                Payment(
                    businessId = bizId,
                    receiptNumber = receiptNo,
                    direction = direction,
                    paymentMode = paymentMode,
                    amountPaise = amountPaise,
                    paymentDateMillis = dateMillis,
                    customerId = customerId,
                    partyName = customer.name,
                    accountId = targetAccount.id,
                    referenceNumber = referenceNumber,
                    notes = notes
                )
            )

            var remainingToAllocate = amountPaise
            val touchedInvoiceIds = mutableListOf<Long>()
            if (!isAdvance) {
                if (invoiceIdToAllocate != null) {
                    val inv = billingDao.getInvoiceByIdOnce(invoiceIdToAllocate)
                    if (inv != null && inv.balanceDuePaise > 0L) {
                        val alloc = minOf(remainingToAllocate, inv.balanceDuePaise)
                        val newPaid = inv.paidAmountPaise + alloc
                        val newDue = inv.balanceDuePaise - alloc
                        val newStatus = if (newDue == 0L) InvoiceStatus.PAID else InvoiceStatus.PARTIAL
                        billingDao.updateInvoicePaymentState(inv.id, newPaid, newDue, newStatus)
                        touchedInvoiceIds.add(inv.id)
                        accountingDao.insertPaymentAllocation(
                            PaymentAllocation(paymentId = paymentId, invoiceId = inv.id, allocatedAmountPaise = alloc)
                        )
                        advancedDao.insertInvoiceAuditLog(
                            InvoiceAuditLog(
                                businessId = bizId,
                                invoiceId = inv.id,
                                invoiceNumber = inv.invoiceNumber,
                                action = "PAYMENT_ADDED",
                                details = "Recorded ₹${Money.fromPaise(alloc)} via ${paymentMode.label}${if (referenceNumber.isNotBlank()) " (Ref: $referenceNumber)" else ""}"
                            )
                        )
                        remainingToAllocate -= alloc
                    }
                } else {
                    val openInvoices = billingDao.getAllInvoicesOnce(bizId)
                        .filter {
                            it.customerId == customerId &&
                                it.balanceDuePaise > 0L &&
                                (it.status == InvoiceStatus.FINALIZED || it.status == InvoiceStatus.PARTIAL)
                        }
                        .sortedBy { it.invoiceDateMillis }
                    for (inv in openInvoices) {
                        if (remainingToAllocate <= 0L) break
                        val alloc = minOf(remainingToAllocate, inv.balanceDuePaise)
                        val newPaid = inv.paidAmountPaise + alloc
                        val newDue = inv.balanceDuePaise - alloc
                        val newStatus = if (newDue == 0L) InvoiceStatus.PAID else InvoiceStatus.PARTIAL
                        billingDao.updateInvoicePaymentState(inv.id, newPaid, newDue, newStatus)
                        touchedInvoiceIds.add(inv.id)
                        accountingDao.insertPaymentAllocation(
                            PaymentAllocation(paymentId = paymentId, invoiceId = inv.id, allocatedAmountPaise = alloc)
                        )
                        advancedDao.insertInvoiceAuditLog(
                            InvoiceAuditLog(
                                businessId = bizId,
                                invoiceId = inv.id,
                                invoiceNumber = inv.invoiceNumber,
                                action = "PAYMENT_ADDED",
                                details = "Allocated ₹${Money.fromPaise(alloc)} from receipt $receiptNo"
                            )
                        )
                        remainingToAllocate -= alloc
                    }
                }
            }

            if (isAdvance) {
                partyDao.adjustCustomerAdvance(customerId, amountPaise)
            } else {
                partyDao.adjustCustomerBalance(customerId, -amountPaise)
            }

            val creditCode = if (isAdvance) {
                HisabPulseDatabase.Companion.AccountCodes.CUSTOMER_ADVANCES
            } else {
                HisabPulseDatabase.Companion.AccountCodes.RECEIVABLES
            }

            ledgerService.postJournal(
                description = "Customer ${direction.label} $receiptNo from ${customer.name}",
                referenceType = "PAYMENT",
                referenceId = paymentId,
                dateMillis = dateMillis,
                lines = listOf(
                    LedgerPostingLine(
                        accountCode = targetAccount.code,
                        accountIdOverride = targetAccount.id,
                        debitPaise = amountPaise,
                        partyType = "CUSTOMER",
                        partyId = customerId,
                        narration = notes.ifBlank { "Payment received ($receiptNo)" }
                    ),
                    LedgerPostingLine(
                        accountCode = creditCode,
                        creditPaise = amountPaise,
                        partyType = "CUSTOMER",
                        partyId = customerId,
                        narration = notes.ifBlank { "Payment received ($receiptNo)" }
                    )
                ),
                businessId = bizId
            )

            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "CUSTOMER_PAYMENT",
                    entityType = "Payment",
                    entityId = paymentId,
                    summary = "Received ₹${Money.fromPaise(amountPaise)} from ${customer.name} via ${paymentMode.label}"
                )
            )
            paymentId to touchedInvoiceIds
        }.let { (paymentId, touchedIds) ->
            touchedIds.distinct().forEach { invId ->
                val inv = billingDao.getInvoiceByIdOnce(invId)
                if (inv != null) {
                    cloudSyncRepository?.enqueueInvoiceSync(inv, billingDao.getInvoiceItemsOnce(invId))
                }
            }
            paymentId
        }
    }

    suspend fun recordSupplierPayment(
        supplierId: Long,
        amountPaise: Long,
        paymentMode: PaymentMode,
        accountId: Long? = null,
        purchaseIdToAllocate: Long? = null,
        isAdvance: Boolean = false,
        dateMillis: Long = System.currentTimeMillis(),
        referenceNumber: String = "",
        notes: String = ""
    ): Result<Long> = runCatching {
        require(amountPaise > 0L) { "Payment amount must be positive" }
        db.withTransaction {
            val supplier = requireNotNull(partyDao.getSupplierByIdOnce(supplierId)) { "Supplier not found" }
            val bizId = supplier.businessId
            val sourceAccount = ledgerService.resolveAccountForPaymentMode(paymentMode, accountId, bizId)
            val direction = if (isAdvance) PaymentDirection.SUPPLIER_ADVANCE else PaymentDirection.PAID
            val voucherNo = "PAY-${System.currentTimeMillis() % 1000000}"

            val paymentId = accountingDao.insertPayment(
                Payment(
                    businessId = bizId,
                    receiptNumber = voucherNo,
                    direction = direction,
                    paymentMode = paymentMode,
                    amountPaise = amountPaise,
                    paymentDateMillis = dateMillis,
                    supplierId = supplierId,
                    partyName = supplier.name,
                    accountId = sourceAccount.id,
                    referenceNumber = referenceNumber,
                    notes = notes
                )
            )

            var remaining = amountPaise
            if (!isAdvance) {
                if (purchaseIdToAllocate != null) {
                    val pur = billingDao.getPurchaseByIdOnce(purchaseIdToAllocate)
                    if (pur != null && pur.balanceDuePaise > 0L) {
                        val alloc = minOf(remaining, pur.balanceDuePaise)
                        val newPaid = pur.paidAmountPaise + alloc
                        val newDue = pur.balanceDuePaise - alloc
                        val newStatus = if (newDue == 0L) PurchaseStatus.PAID else PurchaseStatus.PARTIAL
                        billingDao.updatePurchasePaymentState(pur.id, newPaid, newDue, newStatus)
                        accountingDao.insertPaymentAllocation(
                            PaymentAllocation(paymentId = paymentId, purchaseId = pur.id, allocatedAmountPaise = alloc)
                        )
                    }
                } else {
                    val openPurchases = billingDao.getAllPurchasesOnce(bizId)
                        .filter {
                            it.supplierId == supplierId &&
                                it.balanceDuePaise > 0L &&
                                (it.status == PurchaseStatus.FINALIZED || it.status == PurchaseStatus.PARTIAL)
                        }
                        .sortedBy { it.purchaseDateMillis }
                    for (pur in openPurchases) {
                        if (remaining <= 0L) break
                        val alloc = minOf(remaining, pur.balanceDuePaise)
                        val newPaid = pur.paidAmountPaise + alloc
                        val newDue = pur.balanceDuePaise - alloc
                        val newStatus = if (newDue == 0L) PurchaseStatus.PAID else PurchaseStatus.PARTIAL
                        billingDao.updatePurchasePaymentState(pur.id, newPaid, newDue, newStatus)
                        accountingDao.insertPaymentAllocation(
                            PaymentAllocation(paymentId = paymentId, purchaseId = pur.id, allocatedAmountPaise = alloc)
                        )
                        remaining -= alloc
                    }
                }
            }

            if (isAdvance) {
                partyDao.adjustSupplierAdvance(supplierId, amountPaise)
            } else {
                partyDao.adjustSupplierBalance(supplierId, -amountPaise)
            }

            ledgerService.postJournal(
                description = "Supplier payment $voucherNo to ${supplier.name}",
                referenceType = "PAYMENT",
                referenceId = paymentId,
                dateMillis = dateMillis,
                lines = listOf(
                    LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.PAYABLES,
                        debitPaise = amountPaise,
                        partyType = "SUPPLIER",
                        partyId = supplierId,
                        narration = notes.ifBlank { "Paid to ${supplier.name}" }
                    ),
                    LedgerPostingLine(
                        accountCode = sourceAccount.code,
                        accountIdOverride = sourceAccount.id,
                        creditPaise = amountPaise,
                        partyType = "SUPPLIER",
                        partyId = supplierId,
                        narration = notes.ifBlank { "Paid to ${supplier.name}" }
                    )
                ),
                businessId = bizId
            )

            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "SUPPLIER_PAYMENT",
                    entityType = "Payment",
                    entityId = paymentId,
                    summary = "Paid ₹${Money.fromPaise(amountPaise)} to ${supplier.name} via ${paymentMode.label}"
                )
            )
            paymentId
        }
    }

    suspend fun saveExpense(expense: Expense): Result<Long> = runCatching {
        require(expense.amountPaise > 0L) { "Expense amount must be greater than zero" }
        db.withTransaction {
            val isNew = expense.id == 0L
            val activeBiz = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            val existing = if (!isNew) accountingDao.getAllExpensesOnce(activeBiz).firstOrNull { it.id == expense.id } else null
            val bizId = existing?.businessId ?: activeBiz

            val account = ledgerService.resolveAccountForPaymentMode(expense.paymentMode, expense.accountId, bizId)
            val savedId = accountingDao.insertExpense(
                expense.copy(
                    businessId = bizId,
                    accountId = account.id,
                    updatedAt = System.currentTimeMillis()
                )
            )

            if (existing != null) {
                val oldAccount = ledgerService.resolveAccountForPaymentMode(existing.paymentMode, existing.accountId, bizId)
                ledgerService.postJournal(
                    description = "Reversal for edited expense (${existing.categoryName})",
                    referenceType = "EXPENSE_EDIT_REV",
                    referenceId = savedId,
                    dateMillis = System.currentTimeMillis(),
                    lines = listOf(
                        LedgerPostingLine(oldAccount.code, accountIdOverride = oldAccount.id, debitPaise = existing.amountPaise),
                        LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.OPERATING_EXPENSES, creditPaise = existing.amountPaise)
                    ),
                    businessId = bizId
                )
            }

            ledgerService.postJournal(
                description = "Expense: ${expense.categoryName} - ${expense.description}".trimEnd(' ', '-'),
                referenceType = "EXPENSE",
                referenceId = savedId,
                dateMillis = expense.expenseDateMillis,
                lines = listOf(
                    LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.OPERATING_EXPENSES,
                        debitPaise = expense.amountPaise,
                        narration = expense.description.ifBlank { expense.categoryName }
                    ),
                    LedgerPostingLine(
                        accountCode = account.code,
                        accountIdOverride = account.id,
                        creditPaise = expense.amountPaise,
                        narration = expense.description.ifBlank { expense.categoryName }
                    )
                ),
                businessId = bizId
            )

            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = if (isNew) "ADD_EXPENSE" else "EDIT_EXPENSE",
                    entityType = "Expense",
                    entityId = savedId,
                    summary = "${expense.categoryName}: ₹${Money.fromPaise(expense.amountPaise)}"
                )
            )
            savedId
        }
    }

    suspend fun deleteExpenseSafely(expense: Expense) = db.withTransaction {
        val bizId = expense.businessId
        val account = ledgerService.resolveAccountForPaymentMode(expense.paymentMode, expense.accountId, bizId)
        ledgerService.postJournal(
            description = "Reversal for deleted expense (${expense.categoryName})",
            referenceType = "EXPENSE_DELETE",
            referenceId = expense.id,
            dateMillis = System.currentTimeMillis(),
            lines = listOf(
                LedgerPostingLine(account.code, accountIdOverride = account.id, debitPaise = expense.amountPaise),
                LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.OPERATING_EXPENSES, creditPaise = expense.amountPaise)
            ),
            businessId = bizId
        )
        accountingDao.deleteExpense(expense)
        coreDao.insertAuditLog(
            AuditLog(
                businessId = bizId,
                action = "DELETE_EXPENSE",
                entityType = "Expense",
                entityId = expense.id,
                summary = "Deleted expense ${expense.categoryName} ₹${Money.fromPaise(expense.amountPaise)} with reversing journal entry"
            )
        )
    }

    suspend fun recordCashAdjustment(amountPaise: Long, isCashIn: Boolean, notes: String): Result<Unit> = runCatching {
        require(amountPaise > 0L) { "Amount must be greater than zero" }
        db.withTransaction {
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            val now = System.currentTimeMillis()
            val lines = if (isCashIn) {
                listOf(
                    LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.CASH, debitPaise = amountPaise, narration = notes),
                    LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.OWNER_EQUITY, creditPaise = amountPaise, narration = notes)
                )
            } else {
                listOf(
                    LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.OWNER_EQUITY, debitPaise = amountPaise, narration = notes),
                    LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.CASH, creditPaise = amountPaise, narration = notes)
                )
            }
            ledgerService.postJournal(
                description = if (isCashIn) "Cash In Adjustment: $notes" else "Cash Out Adjustment: $notes",
                referenceType = "CASH_ADJUSTMENT",
                referenceId = null,
                dateMillis = now,
                lines = lines,
                businessId = bizId
            )
        }
    }

    suspend fun addBankAccount(
        name: String,
        accountNumber: String,
        ifsc: String,
        upiId: String,
        openingBalancePaise: Long
    ): Long = db.withTransaction {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val code = "10${(10..99).random()}"
        val id = accountingDao.insertAccount(
            Account(
                businessId = bizId,
                code = code,
                name = name.trim(),
                accountType = AccountType.ASSET,
                isBankAccount = true,
                bankAccountNumber = accountNumber.trim(),
                bankIfsc = ifsc.trim().uppercase(),
                upiId = upiId.trim(),
                openingBalancePaise = openingBalancePaise,
                currentBalancePaise = 0L,
                isSystemAccount = false
            )
        )
        if (openingBalancePaise > 0L) {
            ledgerService.postJournal(
                description = "Opening balance for bank account $name",
                referenceType = "BANK_OPENING",
                referenceId = id,
                dateMillis = System.currentTimeMillis(),
                lines = listOf(
                    LedgerPostingLine(code, accountIdOverride = id, debitPaise = openingBalancePaise),
                    LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.OWNER_EQUITY, creditPaise = openingBalancePaise)
                ),
                businessId = bizId
            )
        }
        id
    }

    fun buildTrialBalance(accounts: List<Account>, entries: List<LedgerEntry>): List<TrialBalanceRow> {
        val byAcc = entries.groupBy { it.accountId }
        return accounts.map { acc ->
            val list = byAcc[acc.id].orEmpty()
            TrialBalanceRow(
                account = acc,
                totalDebitPaise = list.sumOf { it.debitPaise },
                totalCreditPaise = list.sumOf { it.creditPaise }
            )
        }.filter { it.totalDebitPaise != 0L || it.totalCreditPaise != 0L || it.account.currentBalancePaise != 0L }
    }

    fun buildProfitAndLoss(accounts: List<Account>): ProfitAndLossSummary {
        val sales = accounts.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.SALES_REVENUE }?.currentBalancePaise ?: 0L
        val otherIncome = accounts.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.OTHER_INCOME }?.currentBalancePaise ?: 0L
        val cogs = accounts.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.COGS }?.currentBalancePaise ?: 0L
        val operatingExpenses = accounts.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.OPERATING_EXPENSES }?.currentBalancePaise ?: 0L
        val gross = sales - cogs
        val net = gross + otherIncome - operatingExpenses
        return ProfitAndLossSummary(
            salesRevenuePaise = sales,
            otherIncomePaise = otherIncome,
            cogsPaise = cogs,
            operatingExpensesPaise = operatingExpenses,
            grossProfitPaise = gross,
            netProfitPaise = net
        )
    }

    fun buildBalanceSheet(accounts: List<Account>): BalanceSheetSummary {
        val assets = accounts.filter { it.accountType == AccountType.ASSET && it.currentBalancePaise != 0L }
        val liabilities = accounts.filter { it.accountType == AccountType.LIABILITY && it.currentBalancePaise != 0L }
        val equity = accounts.filter { it.accountType == AccountType.EQUITY && it.currentBalancePaise != 0L }
        val pnl = buildProfitAndLoss(accounts)
        val totalAssets = accounts.filter { it.accountType == AccountType.ASSET }.sumOf { it.currentBalancePaise }
        val totalLiab = accounts.filter { it.accountType == AccountType.LIABILITY }.sumOf { it.currentBalancePaise }
        val totalEq = accounts.filter { it.accountType == AccountType.EQUITY }.sumOf { it.currentBalancePaise }
        return BalanceSheetSummary(
            assetAccounts = assets,
            liabilityAccounts = liabilities,
            equityAccounts = equity,
            netProfitPaise = pnl.netProfitPaise,
            totalAssetsPaise = totalAssets,
            totalLiabilitiesAndEquityPaise = totalLiab + totalEq + pnl.netProfitPaise
        )
    }
}
