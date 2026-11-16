package com.example.data.repository

import com.example.data.local.HisabPulseDatabase
import com.example.data.local.dao.AccountingDao
import com.example.data.local.entities.Account
import com.example.data.local.entities.AccountType
import com.example.data.local.entities.JournalEntry
import com.example.data.local.entities.LedgerEntry
import com.example.data.local.entities.PaymentMode

data class LedgerPostingLine(
    val accountCode: String,
    val accountIdOverride: Long? = null,
    val debitPaise: Long = 0L,
    val creditPaise: Long = 0L,
    val partyType: String = "NONE",
    val partyId: Long? = null,
    val narration: String = ""
)

class DoubleEntryLedgerService(private val accountingDao: AccountingDao) {

    suspend fun resolveAccountForPaymentMode(
        mode: PaymentMode,
        explicitAccountId: Long? = null,
        businessId: Long = 1L
    ): Account {
        if (explicitAccountId != null) {
            accountingDao.getAccountById(explicitAccountId)?.let {
                if (it.businessId == businessId) return it
            }
        }
        val code = if (mode == PaymentMode.CASH) {
            HisabPulseDatabase.Companion.AccountCodes.CASH
        } else {
            HisabPulseDatabase.Companion.AccountCodes.BANK
        }
        return requireNotNull(accountingDao.getAccountByCode(code, businessId)) {
            "System account $code missing for business $businessId"
        }
    }

    /**
     * Posts a balanced double-entry JournalEntry and corresponding LedgerEntry lines,
     * updating Account balances atomically. Callers must wrap in db.withTransaction {}.
     */
    suspend fun postJournal(
        description: String,
        referenceType: String,
        referenceId: Long?,
        dateMillis: Long,
        lines: List<LedgerPostingLine>,
        businessId: Long = 1L
    ): Long {
        val nonZero = lines.filter { it.debitPaise > 0L || it.creditPaise > 0L }
        if (nonZero.isEmpty()) return -1L

        val totalDebit = nonZero.sumOf { it.debitPaise }
        val totalCredit = nonZero.sumOf { it.creditPaise }
        require(totalDebit == totalCredit) {
            "Double-entry imbalance: debit=$totalDebit credit=$totalCredit for $description"
        }

        val entryNumber = "JE-${System.currentTimeMillis() % 10000000}-${(100..999).random()}"
        val journalId = accountingDao.insertJournalEntry(
            JournalEntry(
                businessId = businessId,
                entryNumber = entryNumber,
                entryDateMillis = dateMillis,
                description = description,
                referenceType = referenceType,
                referenceId = referenceId,
                totalDebitPaise = totalDebit,
                totalCreditPaise = totalCredit
            )
        )

        val resolvedEntries = mutableListOf<LedgerEntry>()
        for (line in nonZero) {
            val account = if (line.accountIdOverride != null) {
                accountingDao.getAccountById(line.accountIdOverride)
            } else {
                accountingDao.getAccountByCode(line.accountCode, businessId)
            } ?: error("Missing account for code=${line.accountCode} id=${line.accountIdOverride} business=$businessId")

            val delta = when (account.accountType) {
                AccountType.ASSET, AccountType.EXPENSE -> line.debitPaise - line.creditPaise
                AccountType.LIABILITY, AccountType.INCOME, AccountType.EQUITY -> line.creditPaise - line.debitPaise
            }
            accountingDao.adjustAccountBalance(account.id, delta)

            resolvedEntries += LedgerEntry(
                businessId = businessId,
                journalEntryId = journalId,
                accountId = account.id,
                accountName = account.name,
                entryDateMillis = dateMillis,
                debitPaise = line.debitPaise,
                creditPaise = line.creditPaise,
                partyType = line.partyType,
                partyId = line.partyId,
                narration = line.narration.ifBlank { description }
            )
        }

        accountingDao.insertLedgerEntries(resolvedEntries)
        return journalId
    }
}
