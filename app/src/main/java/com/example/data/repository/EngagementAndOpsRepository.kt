package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.Customer
import com.example.data.local.entities.DailyClosing
import com.example.data.local.entities.DailyClosingEntry
import com.example.data.local.entities.Expense
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.LoyaltyAccount
import com.example.data.local.entities.LoyaltyConfiguration
import com.example.data.local.entities.LoyaltyTier
import com.example.data.local.entities.LoyaltyTransaction
import com.example.data.local.entities.LoyaltyTxnType
import com.example.data.local.entities.PaymentDirection
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.local.entities.Product
import com.example.data.local.entities.ReminderChannel
import com.example.data.local.entities.ReminderHistory
import com.example.data.local.entities.UpiConfiguration
import com.example.data.local.entities.VoiceCommandHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface ParsedVoiceAction {
    data class CreateInvoiceDraft(val qty: BigDecimal, val itemQuery: String) : ParsedVoiceAction
    data class CreateExpenseDraft(val amountRupees: BigDecimal, val category: String, val notes: String) : ParsedVoiceAction
    data class SearchProduct(val query: String) : ParsedVoiceAction
    data class SearchCustomer(val query: String) : ParsedVoiceAction
    data class SearchInvoice(val invoiceQuery: String) : ParsedVoiceAction
    data object ShowTodaysSales : ParsedVoiceAction
    data object CheckCashBalance : ParsedVoiceAction
    data object ShowLowStock : ParsedVoiceAction
    data object ShowPendingPayments : ParsedVoiceAction
    data class OpenScreen(val screenName: String) : ParsedVoiceAction
    data class Unrecognized(val rawText: String) : ParsedVoiceAction
}

data class OverdueCustomerItem(
    val customer: Customer,
    val totalPendingPaise: Long,
    val oldestOverdueDays: Int,
    val unpaidInvoices: List<Invoice>
)

data class DailyClosingPreview(
    val closingDateIso: String,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val alreadyClosed: Boolean,
    val existingClosing: DailyClosing?,
    val openingCashPaise: Long,
    val cashSalesPaise: Long,
    val cashReceiptsPaise: Long,
    val cashExpensesPaise: Long,
    val cashPaymentsPaise: Long,
    val cashRefundsPaise: Long,
    val expectedClosingCashPaise: Long,
    val upiCollectionsPaise: Long,
    val bankCollectionsPaise: Long,
    val creditSalesPaise: Long,
    val totalSalesPaise: Long,
    val totalExpensesPaise: Long,
    val entries: List<DailyClosingEntry>
)

@OptIn(ExperimentalCoroutinesApi::class)
class EngagementAndOpsRepository(
    private val db: HisabPulseDatabase,
    private val billingRepository: BillingRepository,
    private val accountingRepository: AccountingRepository
) {
    private val coreDao = db.coreDao()
    private val partyDao = db.partyDao()
    private val inventoryDao = db.inventoryDao()
    private val billingDao = db.billingDao()
    private val accountingDao = db.accountingDao()
    private val advancedDao = db.advancedDao()

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val voiceHistory: Flow<List<VoiceCommandHistory>> = activeBizFlow.flatMapLatest { advancedDao.observeVoiceHistory(it) }
    val reminderHistory: Flow<List<ReminderHistory>> = activeBizFlow.flatMapLatest { advancedDao.observeReminderHistory(it) }
    val printerConfigs: Flow<List<PrinterConfiguration>> = activeBizFlow.flatMapLatest { advancedDao.observePrinterConfigs(it) }
    val upiConfigs: Flow<List<UpiConfiguration>> = activeBizFlow.flatMapLatest { advancedDao.observeUpiConfigs(it) }
    val loyaltyConfig: Flow<LoyaltyConfiguration?> = activeBizFlow.flatMapLatest { advancedDao.observeLoyaltyConfig(it) }
    val loyaltyAccounts: Flow<List<LoyaltyAccount>> = activeBizFlow.flatMapLatest { advancedDao.observeLoyaltyAccounts(it) }
    val loyaltyTransactions: Flow<List<LoyaltyTransaction>> = activeBizFlow.flatMapLatest { advancedDao.observeLoyaltyTransactions(it) }
    val dailyClosings: Flow<List<DailyClosing>> = activeBizFlow.flatMapLatest { advancedDao.observeDailyClosings(it) }

    // ── 1. Voice Business Assistant ──

    fun parseVoiceCommand(rawText: String): ParsedVoiceAction {
        val text = rawText.trim()
        val lower = text.lowercase(Locale.ROOT)
        if (text.isBlank()) return ParsedVoiceAction.Unrecognized(text)

        // Expense: "add 500 rupees transport expense" / "expense 500 rent"
        val expenseRegex = Regex("(?:add|record|log)?\\s*(?:rs\\.?|₹|inr)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rupees|rs|inr)?\\s*(.+?)\\s*expense", RegexOption.IGNORE_CASE)
        expenseRegex.find(text)?.let { match ->
            val amt = match.groupValues[1].toBigDecimalOrNull() ?: BigDecimal.ZERO
            val cat = match.groupValues[2].trim().replaceFirstChar { it.uppercase() }.ifBlank { "Miscellaneous" }
            if (amt > BigDecimal.ZERO) {
                return ParsedVoiceAction.CreateExpenseDraft(amt, cat, text)
            }
        }
        val altExpense = Regex("expense\\s+(?:of\\s+)?(?:rs\\.?|₹)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:for|on)?\\s*(.*)", RegexOption.IGNORE_CASE)
        altExpense.find(text)?.let { match ->
            val amt = match.groupValues[1].toBigDecimalOrNull() ?: BigDecimal.ZERO
            val cat = match.groupValues[2].trim().replaceFirstChar { it.uppercase() }.ifBlank { "Miscellaneous" }
            if (amt > BigDecimal.ZERO) {
                return ParsedVoiceAction.CreateExpenseDraft(amt, cat, text)
            }
        }

        // Invoice / Add item draft: "create a new invoice" or "add 5 LED bulbs" or "bill 2 wireless scanner"
        if (lower.contains("new invoice") || lower.contains("create invoice") || lower.contains("create bill")) {
            return ParsedVoiceAction.CreateInvoiceDraft(BigDecimal.ONE, "")
        }
        val addItemRegex = Regex("(?:add|bill|sell)\\s+(\\d+(?:\\.\\d+)?)\\s+(.+)", RegexOption.IGNORE_CASE)
        addItemRegex.find(text)?.let { match ->
            val qty = match.groupValues[1].toBigDecimalOrNull() ?: BigDecimal.ONE
            val item = match.groupValues[2].trim()
            return ParsedVoiceAction.CreateInvoiceDraft(qty, item)
        }

        // Business Queries
        if (lower.contains("today") && lower.contains("sale")) return ParsedVoiceAction.ShowTodaysSales
        if (lower.contains("cash") && (lower.contains("available") || lower.contains("balance") || lower.contains("much"))) {
            return ParsedVoiceAction.CheckCashBalance
        }
        if (lower.contains("low") && lower.contains("stock") || lower.contains("out of stock")) {
            return ParsedVoiceAction.ShowLowStock
        }
        if (lower.contains("pending payment") || lower.contains("outstanding") || lower.contains("due")) {
            return ParsedVoiceAction.ShowPendingPayments
        }

        // Search invoice number
        val invMatch = Regex("(?:search|find|open)\\s+invoice\\s+([a-zA-Z0-9\\-]+)", RegexOption.IGNORE_CASE).find(text)
        if (invMatch != null) {
            return ParsedVoiceAction.SearchInvoice(invMatch.groupValues[1])
        }

        // Search customer / product
        val custMatch = Regex("(?:search|find)\\s+customer\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (custMatch != null) return ParsedVoiceAction.SearchCustomer(custMatch.groupValues[1].trim())

        val prodMatch = Regex("(?:search|find|stock\\s+of|check\\s+stock)\\s+(?:product\\s+|item\\s+)?(.+)", RegexOption.IGNORE_CASE).find(text)
        if (prodMatch != null) return ParsedVoiceAction.SearchProduct(prodMatch.groupValues[1].trim())

        // Open screens / reports
        if (lower.contains("report") || lower.contains("profit") || lower.contains("gst") || lower.contains("health") || lower.contains("closing")) {
            return ParsedVoiceAction.OpenScreen(text)
        }

        return ParsedVoiceAction.Unrecognized(text)
    }

    suspend fun recordVoiceHistory(
        recognizedText: String,
        parsedIntent: String,
        actionSummary: String,
        wasOnlineRecognizer: Boolean,
        confirmedAndExecuted: Boolean
    ) {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        advancedDao.insertVoiceHistory(
            VoiceCommandHistory(
                businessId = bizId,
                recognizedText = recognizedText,
                parsedIntent = parsedIntent,
                actionSummary = actionSummary,
                wasOnlineRecognizer = wasOnlineRecognizer,
                confirmedAndExecuted = confirmedAndExecuted
            )
        )
    }

    suspend fun executeConfirmedVoiceExpense(
        amountRupees: BigDecimal,
        categoryName: String,
        description: String,
        recognizedText: String
    ): Result<Long> {
        val paise = Money.safeToPaise(amountRupees)
        val res = accountingRepository.saveExpense(
            Expense(
                categoryName = categoryName.ifBlank { "Miscellaneous" },
                amountPaise = paise,
                paymentMode = PaymentMode.CASH,
                description = description.ifBlank { "Voice confirmed expense" }
            )
        )
        if (res.isSuccess) {
            recordVoiceHistory(
                recognizedText = recognizedText,
                parsedIntent = "CREATE_EXPENSE",
                actionSummary = "Confirmed expense ₹${Money.formatPlain(amountRupees)} ($categoryName)",
                wasOnlineRecognizer = false,
                confirmedAndExecuted = true
            )
        }
        return res
    }

    suspend fun executeConfirmedVoiceDraftInvoice(
        itemQuery: String,
        quantity: BigDecimal,
        customerName: String = "Walk-in Customer",
        recognizedText: String
    ): Result<Long> {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val products = inventoryDao.getAllProductsOnce(bizId).filter { it.isActive }
        val matchedProduct = products.firstOrNull {
            itemQuery.isNotBlank() && it.name.contains(itemQuery, ignoreCase = true)
        }
        val line = if (matchedProduct != null) {
            InvoiceLineDraft(
                productId = matchedProduct.id,
                productName = matchedProduct.name,
                productCode = matchedProduct.productCode,
                hsnSacCode = matchedProduct.hsnSacCode,
                unitName = matchedProduct.unitName,
                quantity = quantity.max(BigDecimal.ONE),
                rate = Money.fromPaise(matchedProduct.sellingPricePaise),
                purchaseCost = Money.fromPaise(matchedProduct.purchasePricePaise),
                taxRatePercent = Money.fromPaise(matchedProduct.taxRatePercentScaled),
                isTaxInclusive = matchedProduct.isTaxInclusive
            )
        } else {
            InvoiceLineDraft(
                productId = null,
                productName = itemQuery.ifBlank { "Voice Draft Item" },
                quantity = quantity.max(BigDecimal.ONE),
                rate = BigDecimal.ZERO
            )
        }

        val res = billingRepository.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                documentType = InvoiceDocumentType.TAX_INVOICE,
                saveAsDraft = true,
                customerName = customerName.ifBlank { "Walk-in Customer" },
                items = listOf(line),
                notes = "Created as DRAFT via Voice Command: $recognizedText"
            )
        )
        if (res.isSuccess) {
            recordVoiceHistory(
                recognizedText = recognizedText,
                parsedIntent = "CREATE_DRAFT_INVOICE",
                actionSummary = "Created draft invoice #${res.getOrNull()} (${line.productName} x $quantity)",
                wasOnlineRecognizer = false,
                confirmedAndExecuted = true
            )
        }
        return res
    }

    suspend fun buildVoiceReadbackSummary(): String {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val todayInvoices = getTodayFinalizedInvoices(bizId)
        val todayTotal = todayInvoices.sumOf { it.grandTotalPaise }
        val cashBal = accountingDao.getAccountByCode(HisabPulseDatabase.Companion.AccountCodes.CASH, bizId)?.currentBalancePaise ?: 0L
        val receivables = partyDao.getAllCustomersOnce(bizId).sumOf { it.currentBalancePaise.coerceAtLeast(0L) }
        val lowStock = inventoryDao.getAllProductsOnce(bizId).count { it.isActive && !it.isServiceItem && it.currentStockScaled <= it.minStockScaled }
        return "Today's sales total rupees ${Money.formatPlain(Money.fromPaise(todayTotal))} across ${todayInvoices.size} invoices. " +
            "Cash in hand is rupees ${Money.formatPlain(Money.fromPaise(cashBal))}. " +
            "Pending customer receivables are rupees ${Money.formatPlain(Money.fromPaise(receivables))}. " +
            "$lowStock items are low or out of stock."
    }

    suspend fun getTodayFinalizedInvoices(businessId: Long? = null): List<Invoice> {
        val bizId = businessId ?: coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val startOfDay = startOfDayMillis(System.currentTimeMillis())
        return billingDao.getAllInvoicesOnce(bizId).filter {
            it.invoiceDateMillis >= startOfDay &&
                it.status != InvoiceStatus.DRAFT &&
                it.status != InvoiceStatus.HELD_POS &&
                it.status != InvoiceStatus.CANCELLED &&
                it.documentType.affectsLedger &&
                it.documentType != InvoiceDocumentType.SALES_RETURN &&
                it.documentType != InvoiceDocumentType.CREDIT_NOTE
        }
    }

    // ── 2. Payment Reminders ──

    suspend fun getOverdueCustomers(): List<OverdueCustomerItem> {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val customers = partyDao.getAllCustomersOnce(bizId).filter { it.isActive && it.currentBalancePaise > 0L }
        val invoices = billingDao.getAllInvoicesOnce(bizId)
            .filter {
                it.balanceDuePaise > 0L &&
                    (it.status == InvoiceStatus.FINALIZED || it.status == InvoiceStatus.PARTIAL) &&
                    it.documentType.affectsLedger
            }
        val now = System.currentTimeMillis()
        return customers.map { c ->
            val custInvoices = invoices.filter { it.customerId == c.id }.sortedBy { it.invoiceDateMillis }
            val oldest = custInvoices.firstOrNull()
            val dueRef = oldest?.dueDateMillis ?: oldest?.let { it.invoiceDateMillis + c.defaultDueDays * 86_400_000L } ?: now
            val overdueDays = ((now - dueRef) / 86_400_000L).toInt().coerceAtLeast(0)
            OverdueCustomerItem(
                customer = c,
                totalPendingPaise = c.currentBalancePaise,
                oldestOverdueDays = overdueDays,
                unpaidInvoices = custInvoices
            )
        }.sortedByDescending { it.totalPendingPaise }
    }

    suspend fun buildReminderMessage(
        customerName: String,
        amountDuePaise: Long,
        invoiceNumber: String,
        languageCode: String,
        upiId: String = ""
    ): String {
        val biz = coreDao.getBusinessProfileOnce()?.businessName ?: "Our Store"
        val amt = Money.formatPlain(Money.fromPaise(amountDuePaise))
        val upiSuffix = if (upiId.isNotBlank()) " UPI: $upiId" else ""
        return when (languageCode.lowercase()) {
            "hi" -> "नमस्ते $customerName जी, $biz की ओर से अनुस्मारक: आपका बकाया ₹$amt${if (invoiceNumber.isNotBlank()) " (बिल #$invoiceNumber)" else ""} लंबित है। कृपया शीघ्र भुगतान करें।$upiSuffix धन्यवाद!"
            "gu" -> "નમસ્તે $customerName, $biz તરફથી યાદ અપાવવાનું કે તમારું ₹$amt નું પેમેન્ટ${if (invoiceNumber.isNotBlank()) " (બિલ #$invoiceNumber)" else ""} બાકી છે. કૃપા કરીને સમયસર ચૂકવણી કરો.$upiSuffix આભાર!"
            else -> "Hello $customerName, gentle reminder from $biz: your payment of ₹$amt${if (invoiceNumber.isNotBlank()) " for Invoice #$invoiceNumber" else ""} is pending. Kindly settle at your earliest convenience.$upiSuffix Thank you!"
        }
    }

    suspend fun logReminderAttempt(
        customerId: Long,
        customerName: String,
        invoiceId: Long? = null,
        invoiceNumber: String = "",
        amountDuePaise: Long,
        channel: ReminderChannel,
        languageCode: String,
        messageText: String
    ): Long {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val deliveryNote = when (channel) {
            ReminderChannel.WHATSAPP -> "Opened WhatsApp intent with message draft (delivery depends on WhatsApp send)"
            ReminderChannel.SMS -> "Opened SMS composer with message draft (delivery depends on carrier)"
            ReminderChannel.COPY -> "Copied reminder message to clipboard"
            ReminderChannel.MANUAL -> "Marked manually by business owner"
        }
        return advancedDao.insertReminderHistory(
            ReminderHistory(
                businessId = bizId,
                customerId = customerId,
                customerName = customerName,
                invoiceId = invoiceId,
                invoiceNumber = invoiceNumber,
                amountDuePaise = amountDuePaise,
                channel = channel,
                languageCode = languageCode,
                messageText = messageText,
                deliveryNote = deliveryNote
            )
        )
    }

    // ── 3. Loyalty Program ──

    suspend fun saveLoyaltyConfig(config: LoyaltyConfiguration) {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        advancedDao.upsertLoyaltyConfig(config.copy(businessId = bizId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun adjustLoyaltyPointsManually(
        customerId: Long,
        pointsDelta: Long,
        notes: String
    ): Result<Unit> = runCatching {
        require(pointsDelta != 0L) { "Points adjustment cannot be zero" }
        db.withTransaction {
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            val existing = advancedDao.getLoyaltyAccountOnce(bizId, customerId)
                ?: LoyaltyAccount(businessId = bizId, customerId = customerId).let {
                    val newId = advancedDao.upsertLoyaltyAccount(it)
                    it.copy(id = newId)
                }
            val newBalance = existing.pointsBalance + pointsDelta
            require(newBalance >= 0L) { "Loyalty points balance cannot go negative (available: ${existing.pointsBalance})" }

            val newLifetimeEarned = if (pointsDelta > 0L) existing.lifetimePointsEarned + pointsDelta else existing.lifetimePointsEarned
            val newTier = when {
                newLifetimeEarned >= LoyaltyTier.PLATINUM.minPoints -> LoyaltyTier.PLATINUM
                newLifetimeEarned >= LoyaltyTier.GOLD.minPoints -> LoyaltyTier.GOLD
                else -> LoyaltyTier.SILVER
            }
            advancedDao.upsertLoyaltyAccount(
                existing.copy(
                    pointsBalance = newBalance,
                    lifetimePointsEarned = newLifetimeEarned,
                    membershipTier = newTier,
                    updatedAt = System.currentTimeMillis()
                )
            )
            advancedDao.insertLoyaltyTransaction(
                LoyaltyTransaction(
                    businessId = bizId,
                    loyaltyAccountId = existing.id,
                    customerId = customerId,
                    invoiceId = null,
                    txnType = LoyaltyTxnType.ADJUSTED,
                    pointsDelta = pointsDelta,
                    balanceAfter = newBalance,
                    notes = notes.ifBlank { "Manual loyalty adjustment" }
                )
            )
        }
    }

    // ── 4. Printer & UPI Configurations ──

    suspend fun savePrinterConfig(config: PrinterConfiguration): Long {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        return advancedDao.insertPrinterConfig(config.copy(businessId = bizId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePrinterConfig(config: PrinterConfiguration) {
        advancedDao.deletePrinterConfig(config)
    }

    suspend fun saveUpiConfig(config: UpiConfiguration): Long {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val id = advancedDao.insertUpiConfig(config.copy(businessId = bizId, updatedAt = System.currentTimeMillis()))
        if (config.isDefault) {
            coreDao.getBusinessProfileByIdOnce(bizId)?.let { prof ->
                coreDao.upsertBusinessProfile(prof.copy(upiId = config.upiId))
            }
        }
        return id
    }

    suspend fun deleteUpiConfig(config: UpiConfiguration) {
        advancedDao.deleteUpiConfig(config)
    }

    // ── 5. Daily Closing & Cash Register Reconciliation ──

    suspend fun buildDailyClosingPreview(dateMillis: Long = System.currentTimeMillis()): DailyClosingPreview {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateIso = isoFmt.format(Date(dateMillis))
        val dayStart = startOfDayMillis(dateMillis)
        val dayEnd = dayStart + 86_400_000L - 1L

        val existing = advancedDao.getDailyClosingForDate(bizId, dateIso)
        val latestPrev = advancedDao.observeDailyClosings(bizId)
        val prevClosing = latestPrev.firstOrNull()
            ?.firstOrNull { it.closingDateIso < dateIso }

        val cashAccountBal = accountingDao.getAccountByCode(HisabPulseDatabase.Companion.AccountCodes.CASH, bizId)?.currentBalancePaise ?: 0L

        val dayInvoices = billingDao.getAllInvoicesOnce(bizId).filter {
            it.invoiceDateMillis in dayStart..dayEnd &&
                it.status != InvoiceStatus.DRAFT &&
                it.status != InvoiceStatus.HELD_POS &&
                it.status != InvoiceStatus.CANCELLED &&
                it.documentType.affectsLedger
        }
        val dayPayments = accountingDao.getAllPaymentsOnce(bizId).filter {
            it.paymentDateMillis in dayStart..dayEnd
        }
        val dayExpenses = accountingDao.getAllExpensesOnce(bizId).filter {
            it.expenseDateMillis in dayStart..dayEnd
        }

        // Split invoice-originated payments vs standalone customer receipts cleanly via receiptNumber prefix "RCP-INV..." vs "RCP-<timestamp>"
        val invoiceReceiptNumbers = dayInvoices.map { "RCP-${it.invoiceNumber}" }.toSet()

        val cashSales = dayInvoices
            .filter { it.documentType != InvoiceDocumentType.SALES_RETURN && it.documentType != InvoiceDocumentType.CREDIT_NOTE && it.paymentMode == PaymentMode.CASH }
            .sumOf { it.paidAmountPaise }

        val cashRefunds = dayInvoices
            .filter { it.documentType == InvoiceDocumentType.SALES_RETURN || it.documentType == InvoiceDocumentType.CREDIT_NOTE }
            .filter { it.paymentMode == PaymentMode.CASH }
            .sumOf { it.paidAmountPaise } +
            dayPayments.filter { it.direction == PaymentDirection.REFUND_GIVEN && it.paymentMode == PaymentMode.CASH && it.receiptNumber !in invoiceReceiptNumbers }
                .sumOf { it.amountPaise }

        val cashReceipts = dayPayments
            .filter { (it.direction == PaymentDirection.RECEIVED || it.direction == PaymentDirection.CUSTOMER_ADVANCE) && it.paymentMode == PaymentMode.CASH && it.receiptNumber !in invoiceReceiptNumbers }
            .sumOf { it.amountPaise }

        val cashExpenses = dayExpenses.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.amountPaise }
        val cashSupplierPayments = dayPayments
            .filter { (it.direction == PaymentDirection.PAID || it.direction == PaymentDirection.SUPPLIER_ADVANCE) && it.paymentMode == PaymentMode.CASH }
            .sumOf { it.amountPaise }

        val upiCollections = dayPayments
            .filter { (it.direction == PaymentDirection.RECEIVED || it.direction == PaymentDirection.CUSTOMER_ADVANCE) && it.paymentMode == PaymentMode.UPI }
            .sumOf { it.amountPaise }

        val bankCollections = dayPayments
            .filter {
                (it.direction == PaymentDirection.RECEIVED || it.direction == PaymentDirection.CUSTOMER_ADVANCE) &&
                    (it.paymentMode == PaymentMode.BANK_TRANSFER || it.paymentMode == PaymentMode.CARD || it.paymentMode == PaymentMode.CHEQUE)
            }
            .sumOf { it.amountPaise }

        val creditSales = dayInvoices
            .filter { it.documentType != InvoiceDocumentType.SALES_RETURN && it.documentType != InvoiceDocumentType.CREDIT_NOTE }
            .sumOf { it.balanceDuePaise }

        val totalSales = dayInvoices
            .filter { it.documentType != InvoiceDocumentType.SALES_RETURN && it.documentType != InvoiceDocumentType.CREDIT_NOTE }
            .sumOf { it.grandTotalPaise }

        val totalExpenses = dayExpenses.sumOf { it.amountPaise }

        val netDayCashDelta = cashSales + cashReceipts - cashExpenses - cashSupplierPayments - cashRefunds
        val openingCash = prevClosing?.actualCountedCashPaise ?: (cashAccountBal - netDayCashDelta).coerceAtLeast(0L)
        val expectedClosing = openingCash + netDayCashDelta

        val entries = buildList {
            if (cashSales > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "CASH_SALE", label = "Cash Sales", amountPaise = cashSales))
            if (cashReceipts > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "CASH_RECEIPT", label = "Cash Customer Receipts", amountPaise = cashReceipts))
            if (cashExpenses > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "CASH_EXPENSE", label = "Cash Expenses", amountPaise = -cashExpenses))
            if (cashSupplierPayments > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "CASH_PAYMENT", label = "Cash Supplier Payments", amountPaise = -cashSupplierPayments))
            if (cashRefunds > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "CASH_REFUND", label = "Cash Refunds", amountPaise = -cashRefunds))
            if (upiCollections > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "UPI_COLLECTION", label = "UPI Collections", amountPaise = upiCollections))
            if (bankCollections > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "BANK_COLLECTION", label = "Bank / Card Collections", amountPaise = bankCollections))
            if (creditSales > 0L) add(DailyClosingEntry(dailyClosingId = 0L, entryType = "CREDIT_SALE", label = "Credit Sales Pending", amountPaise = creditSales))
        }

        return DailyClosingPreview(
            closingDateIso = dateIso,
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            alreadyClosed = existing != null,
            existingClosing = existing,
            openingCashPaise = openingCash,
            cashSalesPaise = cashSales,
            cashReceiptsPaise = cashReceipts,
            cashExpensesPaise = cashExpenses,
            cashPaymentsPaise = cashSupplierPayments,
            cashRefundsPaise = cashRefunds,
            expectedClosingCashPaise = expectedClosing,
            upiCollectionsPaise = upiCollections,
            bankCollectionsPaise = bankCollections,
            creditSalesPaise = creditSales,
            totalSalesPaise = totalSales,
            totalExpensesPaise = totalExpenses,
            entries = entries
        )
    }

    suspend fun submitDailyClosing(
        preview: DailyClosingPreview,
        actualCountedCashPaise: Long,
        notes: String = ""
    ): Result<Long> = runCatching {
        require(!preview.alreadyClosed) { "Daily closing for ${preview.closingDateIso} is already finalized" }
        require(actualCountedCashPaise >= 0L) { "Counted cash cannot be negative" }
        db.withTransaction {
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            require(advancedDao.getDailyClosingForDate(bizId, preview.closingDateIso) == null) {
                "Daily closing for ${preview.closingDateIso} already exists"
            }
            val diff = actualCountedCashPaise - preview.expectedClosingCashPaise
            val closingId = advancedDao.insertDailyClosing(
                DailyClosing(
                    businessId = bizId,
                    closingDateIso = preview.closingDateIso,
                    windowStartMillis = preview.windowStartMillis,
                    windowEndMillis = preview.windowEndMillis,
                    openingCashPaise = preview.openingCashPaise,
                    cashSalesPaise = preview.cashSalesPaise,
                    cashReceiptsPaise = preview.cashReceiptsPaise,
                    cashExpensesPaise = preview.cashExpensesPaise,
                    cashPaymentsPaise = preview.cashPaymentsPaise,
                    cashRefundsPaise = preview.cashRefundsPaise,
                    expectedClosingCashPaise = preview.expectedClosingCashPaise,
                    actualCountedCashPaise = actualCountedCashPaise,
                    cashDifferencePaise = diff,
                    upiCollectionsPaise = preview.upiCollectionsPaise,
                    bankCollectionsPaise = preview.bankCollectionsPaise,
                    creditSalesPaise = preview.creditSalesPaise,
                    totalSalesPaise = preview.totalSalesPaise,
                    totalExpensesPaise = preview.totalExpensesPaise,
                    notes = notes
                )
            )
            if (preview.entries.isNotEmpty()) {
                advancedDao.insertClosingEntries(preview.entries.map { it.copy(dailyClosingId = closingId) })
            }
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "DAILY_CLOSING",
                    entityType = "DailyClosing",
                    entityId = closingId,
                    summary = "Closed ${preview.closingDateIso}: Expected ₹${Money.fromPaise(preview.expectedClosingCashPaise)}, Counted ₹${Money.fromPaise(actualCountedCashPaise)}"
                )
            )
            closingId
        }
    }

    private fun startOfDayMillis(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
