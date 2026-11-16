package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.Customer
import com.example.data.local.entities.LedgerEntry
import com.example.data.local.entities.Supplier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class PartyRepository(private val db: HisabPulseDatabase) {
    private val partyDao = db.partyDao()
    private val accountingDao = db.accountingDao()
    private val coreDao = db.coreDao()
    private val ledgerService = DoubleEntryLedgerService(accountingDao)

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val customers: Flow<List<Customer>> = activeBizFlow.flatMapLatest { partyDao.observeCustomers(it) }
    val suppliers: Flow<List<Supplier>> = activeBizFlow.flatMapLatest { partyDao.observeSuppliers(it) }
    val totalReceivables: Flow<Long> = activeBizFlow.flatMapLatest { partyDao.observeTotalReceivables(it) }
    val totalPayables: Flow<Long> = activeBizFlow.flatMapLatest { partyDao.observeTotalPayables(it) }

    fun observeCustomer(customerId: Long): Flow<Customer?> = partyDao.observeCustomerById(customerId)
    fun observeSupplier(supplierId: Long): Flow<Supplier?> = partyDao.observeSupplierById(supplierId)
    fun observeCustomerLedger(customerId: Long): Flow<List<LedgerEntry>> =
        accountingDao.observeCustomerLedger(customerId)

    fun observeSupplierLedger(supplierId: Long): Flow<List<LedgerEntry>> =
        accountingDao.observeSupplierLedger(supplierId)

    suspend fun saveCustomer(customer: Customer): Long = db.withTransaction {
        val isNew = customer.id == 0L
        val now = System.currentTimeMillis()
        val activeBiz = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val existing = if (!isNew) partyDao.getCustomerByIdOnce(customer.id) else null
        val bizId = existing?.businessId ?: activeBiz

        val toSave = if (isNew) {
            customer.copy(
                businessId = bizId,
                currentBalancePaise = customer.openingBalancePaise,
                createdAt = now,
                updatedAt = now
            )
        } else {
            val openingDelta = if (existing != null) {
                customer.openingBalancePaise - existing.openingBalancePaise
            } else 0L
            customer.copy(
                businessId = bizId,
                currentBalancePaise = (existing?.currentBalancePaise ?: customer.currentBalancePaise) + openingDelta,
                updatedAt = now
            )
        }
        val id = partyDao.insertCustomer(toSave)

        if (isNew && toSave.openingBalancePaise > 0L) {
            ledgerService.postJournal(
                description = "Opening receivable balance for ${toSave.name}",
                referenceType = "CUSTOMER_OPENING",
                referenceId = id,
                dateMillis = now,
                lines = listOf(
                    LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.RECEIVABLES,
                        debitPaise = toSave.openingBalancePaise,
                        partyType = "CUSTOMER",
                        partyId = id,
                        narration = "Opening balance"
                    ),
                    LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.OWNER_EQUITY,
                        creditPaise = toSave.openingBalancePaise,
                        narration = "Customer opening equity"
                    )
                ),
                businessId = bizId
            )
        }

        coreDao.insertAuditLog(
            AuditLog(
                businessId = bizId,
                action = if (isNew) "CREATE_CUSTOMER" else "UPDATE_CUSTOMER",
                entityType = "Customer",
                entityId = id,
                summary = "${if (isNew) "Created" else "Updated"} customer ${toSave.name}"
            )
        )
        id
    }

    suspend fun saveSupplier(supplier: Supplier): Long = db.withTransaction {
        val isNew = supplier.id == 0L
        val now = System.currentTimeMillis()
        val activeBiz = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val existing = if (!isNew) partyDao.getSupplierByIdOnce(supplier.id) else null
        val bizId = existing?.businessId ?: activeBiz

        val toSave = if (isNew) {
            supplier.copy(
                businessId = bizId,
                currentBalancePaise = supplier.openingBalancePaise,
                createdAt = now,
                updatedAt = now
            )
        } else {
            val openingDelta = if (existing != null) {
                supplier.openingBalancePaise - existing.openingBalancePaise
            } else 0L
            supplier.copy(
                businessId = bizId,
                currentBalancePaise = (existing?.currentBalancePaise ?: supplier.currentBalancePaise) + openingDelta,
                updatedAt = now
            )
        }
        val id = partyDao.insertSupplier(toSave)

        if (isNew && toSave.openingBalancePaise > 0L) {
            ledgerService.postJournal(
                description = "Opening payable balance for ${toSave.name}",
                referenceType = "SUPPLIER_OPENING",
                referenceId = id,
                dateMillis = now,
                lines = listOf(
                    LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.OWNER_EQUITY,
                        debitPaise = toSave.openingBalancePaise,
                        narration = "Supplier opening equity"
                    ),
                    LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.PAYABLES,
                        creditPaise = toSave.openingBalancePaise,
                        partyType = "SUPPLIER",
                        partyId = id,
                        narration = "Opening balance"
                    )
                ),
                businessId = bizId
            )
        }

        coreDao.insertAuditLog(
            AuditLog(
                businessId = bizId,
                action = if (isNew) "CREATE_SUPPLIER" else "UPDATE_SUPPLIER",
                entityType = "Supplier",
                entityId = id,
                summary = "${if (isNew) "Created" else "Updated"} supplier ${toSave.name}"
            )
        )
        id
    }
}
