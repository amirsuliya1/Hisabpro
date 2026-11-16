package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.DateUtils
import com.example.core.GstCalculator
import com.example.core.GstSupplyType
import com.example.core.LineCalculationInput
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceAuditLog
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.LoyaltyAccount
import com.example.data.local.entities.LoyaltyTier
import com.example.data.local.entities.LoyaltyTransaction
import com.example.data.local.entities.LoyaltyTxnType
import com.example.data.local.entities.Payment
import com.example.data.local.entities.PaymentAllocation
import com.example.data.local.entities.PaymentDirection
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PriceHistory
import com.example.data.local.entities.ProductBatch
import com.example.data.local.entities.Purchase
import com.example.data.local.entities.PurchaseDocumentType
import com.example.data.local.entities.PurchaseItem
import com.example.data.local.entities.PurchaseStatus
import com.example.data.local.entities.StockMovement
import com.example.data.local.entities.StockMovementType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

data class InvoiceLineDraft(
    val productId: Long? = null,
    val batchId: Long? = null,
    val productName: String,
    val productCode: String = "",
    val hsnSacCode: String = "",
    val unitName: String = "PCS",
    val quantity: BigDecimal = BigDecimal.ONE,
    val rate: BigDecimal = BigDecimal.ZERO,
    val purchaseCost: BigDecimal = BigDecimal.ZERO,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val discountFlat: BigDecimal = BigDecimal.ZERO,
    val taxRatePercent: BigDecimal = BigDecimal.ZERO,
    val isTaxInclusive: Boolean = false,
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,
    val serialNumber: String = ""
)

data class SaveInvoiceRequest(
    val existingInvoiceId: Long? = null,
    val invoiceNumber: String? = null,
    val documentType: InvoiceDocumentType = InvoiceDocumentType.TAX_INVOICE,
    val saveAsDraft: Boolean = false,
    val holdAsPos: Boolean = false,
    val invoiceDateMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val customerId: Long? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerGstin: String = "",
    val isInterState: Boolean = false,
    val items: List<InvoiceLineDraft>,
    val invoiceDiscountPercent: BigDecimal = BigDecimal.ZERO,
    val invoiceDiscountFlat: BigDecimal = BigDecimal.ZERO,
    val additionalCharges: BigDecimal = BigDecimal.ZERO,
    val shippingCharges: BigDecimal = BigDecimal.ZERO,
    val enableRoundOff: Boolean = true,
    val paidAmount: BigDecimal = BigDecimal.ZERO,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val paymentReference: String = "",
    val redeemLoyaltyPoints: Long = 0L,
    val allowExpiredBatchOverride: Boolean = false,
    val tableNumber: String = "",
    val kotStatus: String = "",
    val linkedOriginalInvoiceId: Long? = null,
    val notes: String = "",
    val termsAndConditions: String = ""
)

data class PurchaseLineDraft(
    val productId: Long? = null,
    val productName: String,
    val hsnSacCode: String = "",
    val unitName: String = "PCS",
    val quantity: BigDecimal = BigDecimal.ONE,
    val rate: BigDecimal = BigDecimal.ZERO,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val taxRatePercent: BigDecimal = BigDecimal.ZERO,
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null
)

data class SavePurchaseRequest(
    val supplierId: Long? = null,
    val supplierName: String = "Cash Supplier",
    val supplierGstin: String = "",
    val supplierBillNumber: String = "",
    val documentType: PurchaseDocumentType = PurchaseDocumentType.PURCHASE_INVOICE,
    val purchaseDateMillis: Long = System.currentTimeMillis(),
    val isInterState: Boolean = false,
    val items: List<PurchaseLineDraft>,
    val additionalCharges: BigDecimal = BigDecimal.ZERO,
    val paidAmount: BigDecimal = BigDecimal.ZERO,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val linkedOriginalPurchaseId: Long? = null,
    val notes: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepository(
    private val db: HisabPulseDatabase,
    private val cloudSyncRepository: CloudSyncRepository? = null
) {
    private val billingDao = db.billingDao()
    private val inventoryDao = db.inventoryDao()
    private val partyDao = db.partyDao()
    private val coreDao = db.coreDao()
    private val accountingDao = db.accountingDao()
    private val advancedDao = db.advancedDao()
    private val ledgerService = DoubleEntryLedgerService(accountingDao)

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val allInvoices: Flow<List<Invoice>> = activeBizFlow.flatMapLatest { billingDao.observeAllInvoices(it) }
    val heldPosBills: Flow<List<Invoice>> = activeBizFlow.flatMapLatest { billingDao.observeHeldPosBills(it) }
    val allPurchases: Flow<List<Purchase>> = activeBizFlow.flatMapLatest { billingDao.observeAllPurchases(it) }

    fun observeInvoice(id: Long): Flow<Invoice?> = billingDao.observeInvoiceById(id)
    fun observeInvoiceItems(invoiceId: Long): Flow<List<InvoiceItem>> = billingDao.observeInvoiceItems(invoiceId)
    fun observeInvoiceAuditLogs(invoiceId: Long): Flow<List<InvoiceAuditLog>> = advancedDao.observeInvoiceAuditLogs(invoiceId)
    fun observeCustomerInvoices(customerId: Long): Flow<List<Invoice>> = billingDao.observeInvoicesForCustomer(customerId)
    fun observePurchase(id: Long): Flow<Purchase?> = billingDao.observePurchaseById(id)
    fun observePurchaseItems(purchaseId: Long): Flow<List<PurchaseItem>> = billingDao.observePurchaseItems(purchaseId)
    fun observeSupplierPurchases(supplierId: Long): Flow<List<Purchase>> = billingDao.observePurchasesForSupplier(supplierId)

    suspend fun getInvoiceWithItemsOnce(invoiceId: Long): Pair<Invoice, List<InvoiceItem>>? {
        val inv = billingDao.getInvoiceByIdOnce(invoiceId) ?: return null
        val items = billingDao.getInvoiceItemsOnce(invoiceId)
        return inv to items
    }

    suspend fun generateNextInvoiceNumber(prefixOverride: String? = null, businessId: Long? = null): String {
        val bizId = businessId ?: coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val profile = coreDao.getBusinessProfileByIdOnce(bizId) ?: coreDao.getBusinessProfileOnce()
        val prefix = prefixOverride ?: profile?.invoicePrefix ?: "INV-"
        var seq = profile?.nextInvoiceNumber ?: 1001L
        var candidate = "$prefix$seq"
        while (billingDao.countByInvoiceNumber(candidate, bizId) > 0) {
            seq++
            candidate = "$prefix$seq"
        }
        return candidate
    }

    suspend fun saveOrFinalizeInvoice(request: SaveInvoiceRequest): Result<Long> = runCatching {
        require(request.items.isNotEmpty()) { "Add at least one item to the bill" }
        request.items.forEach {
            require(it.quantity > BigDecimal.ZERO) { "Quantity must be greater than zero for ${it.productName}" }
            require(it.rate >= BigDecimal.ZERO) { "Rate cannot be negative for ${it.productName}" }
        }

        db.withTransaction {
            val now = System.currentTimeMillis()
            val settings = coreDao.getAppSettingsOnce()
            val existing = request.existingInvoiceId?.let { billingDao.getInvoiceByIdOnce(it) }
            val bizId = existing?.businessId ?: settings?.activeBusinessId ?: 1L

            if (existing != null) {
                require(existing.status == InvoiceStatus.DRAFT || existing.status == InvoiceStatus.HELD_POS) {
                    "Finalized invoices cannot be overwritten. Use Sales Return, Credit Note, or Cancel & Reversal."
                }
            }

            val isFinalizing = !request.saveAsDraft && !request.holdAsPos
            if (isFinalizing && request.documentType.affectsStock &&
                request.documentType != InvoiceDocumentType.SALES_RETURN
            ) {
                val warnExpired = settings?.warnExpiredBatchSale ?: true
                val blockExpired = settings?.blockExpiredBatchSale ?: false
                for (draft in request.items) {
                    val batch = draft.batchId?.let { advancedDao.getBatchByIdOnce(it) }
                    val expiry = draft.expiryDateMillis ?: batch?.expiryDateMillis
                    if (expiry != null && expiry < now) {
                        val bLabel = draft.batchNumber.ifBlank { batch?.batchNumber ?: draft.productName }
                        if (blockExpired) {
                            error("Expired batch '$bLabel' (${DateUtils.formatDate(expiry)}) cannot be sold (blocked in settings).")
                        } else if (warnExpired && !request.allowExpiredBatchOverride) {
                            error("EXPIRED_BATCH_WARNING: Batch '$bLabel' expired on ${DateUtils.formatDate(expiry)}. Confirm override to sell.")
                        }
                    }
                }
            }

            // Validate & calculate loyalty redemption if requested on a finalized ledger-affecting invoice
            var appliedLoyaltyPoints = 0L
            var loyaltyDiscountPaise = 0L
            if (isFinalizing && request.redeemLoyaltyPoints > 0L && request.documentType.affectsLedger &&
                request.documentType != InvoiceDocumentType.SALES_RETURN &&
                request.documentType != InvoiceDocumentType.CREDIT_NOTE
            ) {
                val cid = requireNotNull(request.customerId) { "Select a customer to redeem loyalty points" }
                val loyaltyCfg = advancedDao.getLoyaltyConfigOnce(bizId)
                require(loyaltyCfg?.isEnabled == true) { "Loyalty program is currently disabled" }
                val account = requireNotNull(advancedDao.getLoyaltyAccountOnce(bizId, cid)) {
                    "Customer has no loyalty points balance"
                }
                require(request.redeemLoyaltyPoints >= loyaltyCfg.minRedeemPoints) {
                    "Minimum ${loyaltyCfg.minRedeemPoints} points required for redemption"
                }
                require(request.redeemLoyaltyPoints <= account.pointsBalance) {
                    "Insufficient loyalty points (available: ${account.pointsBalance})"
                }
                appliedLoyaltyPoints = request.redeemLoyaltyPoints.coerceAtMost(loyaltyCfg.maxRedeemPointsPerInvoice)
                loyaltyDiscountPaise = appliedLoyaltyPoints * loyaltyCfg.pointValuePaise
            }

            val supplyType = when {
                request.documentType == InvoiceDocumentType.NON_GST_INVOICE -> GstSupplyType.NON_GST
                request.isInterState -> GstSupplyType.INTER_STATE
                else -> GstSupplyType.INTRA_STATE
            }

            val lineInputs = request.items.map {
                LineCalculationInput(
                    quantity = it.quantity,
                    unitPrice = it.rate,
                    discountPercent = it.discountPercent,
                    discountAmount = it.discountFlat,
                    taxRatePercent = it.taxRatePercent,
                    isTaxInclusive = it.isTaxInclusive
                )
            }
            val lineResults = lineInputs.map { GstCalculator.calculateLine(it, supplyType) }
            val effectiveFlatDiscount = request.invoiceDiscountFlat + Money.fromPaise(loyaltyDiscountPaise)
            val docResult = GstCalculator.calculateDocument(
                lines = lineInputs,
                supplyType = supplyType,
                invoiceDiscountPercent = request.invoiceDiscountPercent,
                invoiceDiscountFlat = effectiveFlatDiscount,
                additionalCharges = request.additionalCharges,
                shippingCharges = request.shippingCharges,
                enableRoundOff = request.enableRoundOff
            )

            val grandTotalPaise = Money.safeToPaise(docResult.grandTotal)
            val paidPaise = if (request.saveAsDraft || request.holdAsPos) {
                Money.safeToPaise(request.paidAmount.max(BigDecimal.ZERO)).coerceAtMost(grandTotalPaise)
            } else if (!request.documentType.affectsLedger) {
                0L
            } else {
                Money.safeToPaise(request.paidAmount.max(BigDecimal.ZERO)).coerceAtMost(grandTotalPaise)
            }
            val balanceDuePaise = (grandTotalPaise - paidPaise).coerceAtLeast(0L)

            val status = when {
                request.holdAsPos -> InvoiceStatus.HELD_POS
                request.saveAsDraft -> InvoiceStatus.DRAFT
                !request.documentType.affectsLedger -> InvoiceStatus.FINALIZED
                balanceDuePaise == 0L -> InvoiceStatus.PAID
                paidPaise > 0L -> InvoiceStatus.PARTIAL
                else -> InvoiceStatus.FINALIZED
            }

            val invNumber = when {
                existing != null && !request.invoiceNumber.isNullOrBlank() -> request.invoiceNumber.trim()
                existing != null -> existing.invoiceNumber
                !request.invoiceNumber.isNullOrBlank() -> {
                    val trimmed = request.invoiceNumber.trim()
                    require(billingDao.countByInvoiceNumber(trimmed, bizId) == 0) {
                        "Invoice number $trimmed already exists"
                    }
                    trimmed
                }
                else -> {
                    val prefix = when (request.documentType) {
                        InvoiceDocumentType.ESTIMATE -> "EST-"
                        InvoiceDocumentType.QUOTATION -> "QTN-"
                        InvoiceDocumentType.PROFORMA -> "PRO-"
                        InvoiceDocumentType.DELIVERY_CHALLAN -> "DC-"
                        InvoiceDocumentType.CREDIT_NOTE -> "CN-"
                        InvoiceDocumentType.DEBIT_NOTE -> "DN-"
                        InvoiceDocumentType.SALES_RETURN -> "SR-"
                        else -> null
                    }
                    generateNextInvoiceNumber(prefix, bizId)
                }
            }

            if (existing == null) {
                val profile = coreDao.getBusinessProfileByIdOnce(bizId)
                if (profile != null && invNumber.startsWith(profile.invoicePrefix)) {
                    coreDao.upsertBusinessProfile(
                        profile.copy(nextInvoiceNumber = profile.nextInvoiceNumber + 1L, updatedAt = now)
                    )
                }
            }

            val invoiceEntity = Invoice(
                id = existing?.id ?: 0L,
                businessId = bizId,
                invoiceNumber = invNumber,
                documentType = request.documentType,
                status = status,
                invoiceDateMillis = request.invoiceDateMillis,
                dueDateMillis = request.dueDateMillis,
                customerId = request.customerId,
                customerName = request.customerName.ifBlank { "Walk-in Customer" },
                customerPhone = request.customerPhone,
                customerAddress = request.customerAddress,
                customerGstin = request.customerGstin,
                isInterState = request.isInterState,
                subtotalPaise = Money.safeToPaise(docResult.subtotal),
                itemDiscountPaise = Money.safeToPaise(docResult.itemDiscountTotal),
                invoiceDiscountPercentScaled = Money.safeToPaise(request.invoiceDiscountPercent),
                invoiceDiscountFlatPaise = Money.safeToPaise(docResult.invoiceDiscountAmount),
                taxableAmountPaise = Money.safeToPaise(docResult.taxableAmount),
                cgstPaise = Money.safeToPaise(docResult.cgstTotal),
                sgstPaise = Money.safeToPaise(docResult.sgstTotal),
                igstPaise = Money.safeToPaise(docResult.igstTotal),
                totalTaxPaise = Money.safeToPaise(docResult.totalTax),
                additionalChargesPaise = Money.safeToPaise(docResult.additionalCharges),
                shippingChargesPaise = Money.safeToPaise(docResult.shippingCharges),
                roundOffPaise = Money.safeToPaise(docResult.roundOff),
                grandTotalPaise = grandTotalPaise,
                paidAmountPaise = paidPaise,
                balanceDuePaise = balanceDuePaise,
                paymentMode = request.paymentMode,
                paymentReference = request.paymentReference,
                loyaltyPointsRedeemed = appliedLoyaltyPoints,
                loyaltyDiscountPaise = loyaltyDiscountPaise,
                tableNumber = request.tableNumber,
                kotStatus = request.kotStatus,
                linkedOriginalInvoiceId = request.linkedOriginalInvoiceId,
                notes = request.notes,
                termsAndConditions = request.termsAndConditions,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )

            val savedInvoiceId = billingDao.insertInvoice(invoiceEntity)
            if (existing != null) {
                billingDao.deleteInvoiceItems(savedInvoiceId)
            }

            val itemEntities = request.items.mapIndexed { idx, draft ->
                val res = lineResults[idx]
                InvoiceItem(
                    invoiceId = savedInvoiceId,
                    productId = draft.productId,
                    batchId = draft.batchId,
                    productName = draft.productName,
                    productCode = draft.productCode,
                    hsnSacCode = draft.hsnSacCode,
                    unitName = draft.unitName,
                    quantityScaled = Money.safeToPaise(draft.quantity),
                    ratePaise = Money.safeToPaise(draft.rate),
                    purchaseCostPaise = Money.safeToPaise(draft.purchaseCost),
                    discountPercentScaled = Money.safeToPaise(draft.discountPercent),
                    discountAmountPaise = Money.safeToPaise(res.discountAmount),
                    taxRatePercentScaled = Money.safeToPaise(draft.taxRatePercent),
                    isTaxInclusive = draft.isTaxInclusive,
                    taxableAmountPaise = Money.safeToPaise(res.taxableAmount),
                    cgstPaise = Money.safeToPaise(res.cgstAmount),
                    sgstPaise = Money.safeToPaise(res.sgstAmount),
                    igstPaise = Money.safeToPaise(res.igstAmount),
                    lineTotalPaise = Money.safeToPaise(res.lineTotal),
                    batchNumber = draft.batchNumber,
                    expiryDateMillis = draft.expiryDateMillis,
                    serialNumber = draft.serialNumber
                )
            }
            billingDao.insertInvoiceItems(itemEntities)

            if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.HELD_POS) {
                val earnedPoints = applyFinalizedInvoiceEffects(
                    invoice = invoiceEntity.copy(id = savedInvoiceId),
                    items = itemEntities,
                    now = now
                )
                if (earnedPoints > 0L) {
                    billingDao.updateInvoice(
                        invoiceEntity.copy(id = savedInvoiceId, loyaltyPointsEarned = earnedPoints)
                    )
                }
                advancedDao.insertInvoiceAuditLog(
                    InvoiceAuditLog(
                        businessId = bizId,
                        invoiceId = savedInvoiceId,
                        invoiceNumber = invNumber,
                        action = "FINALIZED",
                        details = "${request.documentType.label} finalized for ₹${Money.fromPaise(grandTotalPaise)}",
                        createdAt = now
                    )
                )
            } else {
                coreDao.insertAuditLog(
                    AuditLog(
                        businessId = bizId,
                        action = if (existing == null) "SAVE_DRAFT_INVOICE" else "EDIT_DRAFT_INVOICE",
                        entityType = "Invoice",
                        entityId = savedInvoiceId,
                        summary = "${if (existing == null) "Saved" else "Updated"} ${status.label} $invNumber for ${invoiceEntity.customerName}"
                    )
                )
                advancedDao.insertInvoiceAuditLog(
                    InvoiceAuditLog(
                        businessId = bizId,
                        invoiceId = savedInvoiceId,
                        invoiceNumber = invNumber,
                        action = if (existing == null) "CREATED" else "DRAFT_EDITED",
                        details = "${status.label} saved with ${itemEntities.size} items (Total ₹${Money.fromPaise(grandTotalPaise)})",
                        createdAt = now
                    )
                )
            }
            savedInvoiceId
        }.also { savedId ->
            val savedInv = billingDao.getInvoiceByIdOnce(savedId)
            val savedItems = billingDao.getInvoiceItemsOnce(savedId)
            if (savedInv != null) {
                cloudSyncRepository?.enqueueInvoiceSync(savedInv, savedItems)
                coreDao.getBusinessProfileByIdOnce(savedInv.businessId)?.let {
                    cloudSyncRepository?.enqueueBusinessSync(it)
                }
            }
            savedItems.mapNotNull { it.productId }.distinct().forEach { pid ->
                inventoryDao.getProductByIdOnce(pid)?.let { cloudSyncRepository?.enqueueProductSync(it) }
            }
        }
    }

    private suspend fun applyFinalizedInvoiceEffects(
        invoice: Invoice,
        items: List<InvoiceItem>,
        now: Long
    ): Long {
        val bizId = invoice.businessId
        val allowNegativeStock = coreDao.getAppSettingsOnce()?.allowNegativeStock ?: false
        val isReturnOrCredit = invoice.documentType == InvoiceDocumentType.SALES_RETURN ||
            invoice.documentType == InvoiceDocumentType.CREDIT_NOTE

        var totalCogsPaise = 0L

        if (invoice.documentType.affectsStock) {
            val defaultWarehouseId = inventoryDao.getAllWarehousesOnce(bizId).firstOrNull()?.id
            for (item in items) {
                val productId = item.productId ?: continue
                val product = inventoryDao.getProductByIdOnce(productId) ?: continue
                if (product.isServiceItem) continue

                val delta = if (isReturnOrCredit) item.quantityScaled else -item.quantityScaled
                val newStock = product.currentStockScaled + delta
                require(allowNegativeStock || newStock >= 0L) {
                    "Insufficient stock for ${product.name} (available: ${product.currentStockScaled / 100.0} ${product.unitName})"
                }
                inventoryDao.adjustProductStock(productId, delta, now)

                if (item.batchId != null) {
                    advancedDao.adjustBatchQuantity(item.batchId, delta)
                } else if (item.batchNumber.isNotBlank()) {
                    advancedDao.findBatchByProductAndNumber(productId, item.batchNumber)?.let {
                        advancedDao.adjustBatchQuantity(it.id, delta)
                    }
                }

                inventoryDao.insertStockMovement(
                    StockMovement(
                        businessId = bizId,
                        productId = productId,
                        warehouseId = defaultWarehouseId,
                        movementType = if (isReturnOrCredit) StockMovementType.SALES_RETURN_IN else StockMovementType.SALE_OUT,
                        quantityDeltaScaled = delta,
                        resultingStockScaled = newStock,
                        unitCostPaise = product.purchasePricePaise,
                        referenceType = "INVOICE",
                        referenceId = invoice.id,
                        batchNumber = item.batchNumber,
                        notes = "${invoice.documentType.label} ${invoice.invoiceNumber}",
                        createdAt = now
                    )
                )
                totalCogsPaise += InventoryRepository.computeStockValuePaise(item.quantityScaled, product.purchasePricePaise)
            }
        }

        if (invoice.documentType.affectsLedger) {
            if (invoice.customerId != null) {
                val balDelta = if (isReturnOrCredit) {
                    -(invoice.grandTotalPaise - invoice.paidAmountPaise)
                } else {
                    invoice.balanceDuePaise
                }
                if (balDelta != 0L) {
                    partyDao.adjustCustomerBalance(invoice.customerId, balDelta, now)
                }
            }

            if (invoice.paidAmountPaise > 0L) {
                val settlementAccount = ledgerService.resolveAccountForPaymentMode(
                    mode = invoice.paymentMode,
                    businessId = bizId
                )
                val paymentId = accountingDao.insertPayment(
                    Payment(
                        businessId = bizId,
                        receiptNumber = "RCP-${invoice.invoiceNumber}",
                        direction = if (isReturnOrCredit) PaymentDirection.REFUND_GIVEN else PaymentDirection.RECEIVED,
                        paymentMode = invoice.paymentMode,
                        amountPaise = invoice.paidAmountPaise,
                        paymentDateMillis = invoice.invoiceDateMillis,
                        customerId = invoice.customerId,
                        partyName = invoice.customerName,
                        accountId = settlementAccount.id,
                        referenceNumber = invoice.paymentReference,
                        notes = "Payment on ${invoice.invoiceNumber}",
                        createdAt = now
                    )
                )
                accountingDao.insertPaymentAllocation(
                    PaymentAllocation(
                        paymentId = paymentId,
                        invoiceId = invoice.id,
                        allocatedAmountPaise = invoice.paidAmountPaise
                    )
                )
            }

            val settleCode = if (invoice.paymentMode == PaymentMode.CASH) {
                HisabPulseDatabase.Companion.AccountCodes.CASH
            } else {
                HisabPulseDatabase.Companion.AccountCodes.BANK
            }
            val revenuePortion = (invoice.grandTotalPaise - invoice.totalTaxPaise).coerceAtLeast(0L)

            val lines = mutableListOf<LedgerPostingLine>()
            if (!isReturnOrCredit) {
                if (invoice.paidAmountPaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = settleCode,
                        debitPaise = invoice.paidAmountPaise,
                        partyType = if (invoice.customerId != null) "CUSTOMER" else "NONE",
                        partyId = invoice.customerId,
                        narration = "Paid on ${invoice.invoiceNumber}"
                    )
                }
                if (invoice.balanceDuePaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.RECEIVABLES,
                        debitPaise = invoice.balanceDuePaise,
                        partyType = if (invoice.customerId != null) "CUSTOMER" else "NONE",
                        partyId = invoice.customerId,
                        narration = "Receivable for ${invoice.invoiceNumber}"
                    )
                }
                if (revenuePortion > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.SALES_REVENUE,
                        creditPaise = revenuePortion,
                        narration = "Sales ${invoice.invoiceNumber}"
                    )
                }
                if (invoice.totalTaxPaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.OUTPUT_GST,
                        creditPaise = invoice.totalTaxPaise,
                        narration = "Output GST on ${invoice.invoiceNumber}"
                    )
                }
                if (totalCogsPaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.COGS,
                        debitPaise = totalCogsPaise,
                        narration = "COGS for ${invoice.invoiceNumber}"
                    )
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.INVENTORY,
                        creditPaise = totalCogsPaise,
                        narration = "Inventory sold ${invoice.invoiceNumber}"
                    )
                }
            } else {
                if (revenuePortion > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.SALES_REVENUE,
                        debitPaise = revenuePortion,
                        narration = "Return ${invoice.invoiceNumber}"
                    )
                }
                if (invoice.totalTaxPaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.OUTPUT_GST,
                        debitPaise = invoice.totalTaxPaise,
                        narration = "GST reversal ${invoice.invoiceNumber}"
                    )
                }
                if (invoice.paidAmountPaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = settleCode,
                        creditPaise = invoice.paidAmountPaise,
                        partyType = if (invoice.customerId != null) "CUSTOMER" else "NONE",
                        partyId = invoice.customerId,
                        narration = "Refund on ${invoice.invoiceNumber}"
                    )
                }
                if (invoice.balanceDuePaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.RECEIVABLES,
                        creditPaise = invoice.balanceDuePaise,
                        partyType = if (invoice.customerId != null) "CUSTOMER" else "NONE",
                        partyId = invoice.customerId,
                        narration = "Credit note ${invoice.invoiceNumber}"
                    )
                }
                if (totalCogsPaise > 0L) {
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.INVENTORY,
                        debitPaise = totalCogsPaise,
                        narration = "Returned stock ${invoice.invoiceNumber}"
                    )
                    lines += LedgerPostingLine(
                        accountCode = HisabPulseDatabase.Companion.AccountCodes.COGS,
                        creditPaise = totalCogsPaise,
                        narration = "COGS reversal ${invoice.invoiceNumber}"
                    )
                }
            }

            ledgerService.postJournal(
                description = "${invoice.documentType.label} ${invoice.invoiceNumber} (${invoice.customerName})",
                referenceType = "INVOICE",
                referenceId = invoice.id,
                dateMillis = invoice.invoiceDateMillis,
                lines = lines,
                businessId = bizId
            )
        }

        // Loyalty Points Accounting (non-duplicate & non-negative guarded)
        var pointsEarned = 0L
        if (invoice.customerId != null && invoice.documentType.affectsLedger) {
            val loyaltyCfg = advancedDao.getLoyaltyConfigOnce(bizId)
            if (loyaltyCfg != null && loyaltyCfg.isEnabled) {
                var loyaltyAcc = advancedDao.getLoyaltyAccountOnce(bizId, invoice.customerId)
                if (loyaltyAcc == null) {
                    val accId = advancedDao.upsertLoyaltyAccount(
                        LoyaltyAccount(businessId = bizId, customerId = invoice.customerId)
                    )
                    loyaltyAcc = advancedDao.getLoyaltyAccountOnce(bizId, invoice.customerId)
                        ?: LoyaltyAccount(id = accId, businessId = bizId, customerId = invoice.customerId)
                }

                // Process redemption if applied
                if (invoice.loyaltyPointsRedeemed > 0L &&
                    advancedDao.countLoyaltyTxnForInvoice(invoice.id, LoyaltyTxnType.REDEEMED) == 0
                ) {
                    val redeemPts = invoice.loyaltyPointsRedeemed.coerceAtMost(loyaltyAcc.pointsBalance)
                    if (redeemPts > 0L) {
                        val afterBal = (loyaltyAcc.pointsBalance - redeemPts).coerceAtLeast(0L)
                        loyaltyAcc = loyaltyAcc.copy(
                            pointsBalance = afterBal,
                            lifetimePointsRedeemed = loyaltyAcc.lifetimePointsRedeemed + redeemPts,
                            updatedAt = now
                        )
                        advancedDao.upsertLoyaltyAccount(loyaltyAcc)
                        advancedDao.insertLoyaltyTransaction(
                            LoyaltyTransaction(
                                businessId = bizId,
                                loyaltyAccountId = loyaltyAcc.id,
                                customerId = invoice.customerId,
                                invoiceId = invoice.id,
                                txnType = LoyaltyTxnType.REDEEMED,
                                pointsDelta = -redeemPts,
                                balanceAfter = afterBal,
                                notes = "Redeemed on ${invoice.invoiceNumber}",
                                createdAt = now
                            )
                        )
                    }
                }

                // Award points on non-return sales above minimum invoice threshold
                if (!isReturnOrCredit &&
                    invoice.grandTotalPaise >= loyaltyCfg.minInvoiceAmountPaise &&
                    loyaltyCfg.rupeesPerPoint > 0L &&
                    advancedDao.countLoyaltyTxnForInvoice(invoice.id, LoyaltyTxnType.EARNED) == 0
                ) {
                    val basePoints = invoice.grandTotalPaise / (loyaltyCfg.rupeesPerPoint * 100L)
                    val multPoints = (basePoints * loyaltyAcc.membershipTier.multiplierScaled) / 100L
                    pointsEarned = multPoints.coerceAtLeast(0L)
                    if (pointsEarned > 0L) {
                        val newBal = loyaltyAcc.pointsBalance + pointsEarned
                        val newLifetime = loyaltyAcc.lifetimePointsEarned + pointsEarned
                        val newTier = when {
                            newLifetime >= LoyaltyTier.PLATINUM.minPoints -> LoyaltyTier.PLATINUM
                            newLifetime >= LoyaltyTier.GOLD.minPoints -> LoyaltyTier.GOLD
                            else -> LoyaltyTier.SILVER
                        }
                        loyaltyAcc = loyaltyAcc.copy(
                            pointsBalance = newBal,
                            lifetimePointsEarned = newLifetime,
                            membershipTier = newTier,
                            updatedAt = now
                        )
                        advancedDao.upsertLoyaltyAccount(loyaltyAcc)
                        val expiryAt = if (loyaltyCfg.pointsExpiryDays > 0) {
                            now + loyaltyCfg.pointsExpiryDays * 86_400_000L
                        } else null
                        advancedDao.insertLoyaltyTransaction(
                            LoyaltyTransaction(
                                businessId = bizId,
                                loyaltyAccountId = loyaltyAcc.id,
                                customerId = invoice.customerId,
                                invoiceId = invoice.id,
                                txnType = LoyaltyTxnType.EARNED,
                                pointsDelta = pointsEarned,
                                balanceAfter = newBal,
                                notes = "Earned on ${invoice.invoiceNumber}",
                                expiresAtMillis = expiryAt,
                                createdAt = now
                            )
                        )
                    }
                }
            }
        }

        coreDao.insertAuditLog(
            AuditLog(
                businessId = bizId,
                action = "FINALIZE_INVOICE",
                entityType = "Invoice",
                entityId = invoice.id,
                summary = "Finalized ${invoice.documentType.label} ${invoice.invoiceNumber} for ₹${Money.fromPaise(invoice.grandTotalPaise)}"
            )
        )
        return pointsEarned
    }

    suspend fun cancelFinalizedInvoice(invoiceId: Long, reason: String): Result<Unit> = runCatching {
        db.withTransaction {
            val invoice = requireNotNull(billingDao.getInvoiceByIdOnce(invoiceId)) { "Invoice not found" }
            require(invoice.status != InvoiceStatus.CANCELLED) { "Invoice is already cancelled" }
            val bizId = invoice.businessId
            val now = System.currentTimeMillis()
            if (invoice.status == InvoiceStatus.DRAFT || invoice.status == InvoiceStatus.HELD_POS) {
                billingDao.updateInvoice(invoice.copy(status = InvoiceStatus.CANCELLED, updatedAt = now))
                advancedDao.insertInvoiceAuditLog(
                    InvoiceAuditLog(
                        businessId = bizId,
                        invoiceId = invoiceId,
                        invoiceNumber = invoice.invoiceNumber,
                        action = "CANCELLED_REVERSED",
                        details = "Draft/Held bill cancelled: $reason",
                        createdAt = now
                    )
                )
                return@withTransaction
            }

            val items = billingDao.getInvoiceItemsOnce(invoiceId)
            var totalCogsPaise = 0L
            val isReturnOrCredit = invoice.documentType == InvoiceDocumentType.SALES_RETURN ||
                invoice.documentType == InvoiceDocumentType.CREDIT_NOTE

            if (invoice.documentType.affectsStock) {
                val defaultWh = inventoryDao.getAllWarehousesOnce(bizId).firstOrNull()?.id
                for (item in items) {
                    val productId = item.productId ?: continue
                    val product = inventoryDao.getProductByIdOnce(productId) ?: continue
                    if (product.isServiceItem) continue
                    val delta = if (isReturnOrCredit) -item.quantityScaled else item.quantityScaled
                    val newStock = product.currentStockScaled + delta
                    inventoryDao.adjustProductStock(productId, delta, now)
                    if (item.batchId != null) {
                        advancedDao.adjustBatchQuantity(item.batchId, delta)
                    }
                    inventoryDao.insertStockMovement(
                        StockMovement(
                            businessId = bizId,
                            productId = productId,
                            warehouseId = defaultWh,
                            movementType = if (isReturnOrCredit) StockMovementType.ADJUSTMENT_OUT else StockMovementType.REVERSAL_IN,
                            quantityDeltaScaled = delta,
                            resultingStockScaled = newStock,
                            unitCostPaise = product.purchasePricePaise,
                            referenceType = "INVOICE_CANCEL",
                            referenceId = invoiceId,
                            notes = "Cancelled ${invoice.invoiceNumber}: $reason",
                            createdAt = now
                        )
                    )
                    totalCogsPaise += InventoryRepository.computeStockValuePaise(item.quantityScaled, product.purchasePricePaise)
                }
            }

            if (invoice.documentType.affectsLedger) {
                if (invoice.customerId != null && invoice.balanceDuePaise > 0L) {
                    partyDao.adjustCustomerBalance(invoice.customerId, -invoice.balanceDuePaise, now)
                }
                val settleCode = if (invoice.paymentMode == PaymentMode.CASH) {
                    HisabPulseDatabase.Companion.AccountCodes.CASH
                } else {
                    HisabPulseDatabase.Companion.AccountCodes.BANK
                }
                val revenuePortion = (invoice.grandTotalPaise - invoice.totalTaxPaise).coerceAtLeast(0L)
                val revLines = mutableListOf<LedgerPostingLine>()
                if (revenuePortion > 0L) {
                    revLines += LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.SALES_REVENUE, debitPaise = revenuePortion)
                }
                if (invoice.totalTaxPaise > 0L) {
                    revLines += LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.OUTPUT_GST, debitPaise = invoice.totalTaxPaise)
                }
                if (invoice.paidAmountPaise > 0L) {
                    revLines += LedgerPostingLine(settleCode, creditPaise = invoice.paidAmountPaise)
                }
                if (invoice.balanceDuePaise > 0L) {
                    revLines += LedgerPostingLine(
                        HisabPulseDatabase.Companion.AccountCodes.RECEIVABLES,
                        creditPaise = invoice.balanceDuePaise,
                        partyType = if (invoice.customerId != null) "CUSTOMER" else "NONE",
                        partyId = invoice.customerId
                    )
                }
                if (totalCogsPaise > 0L) {
                    revLines += LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.INVENTORY, debitPaise = totalCogsPaise)
                    revLines += LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.COGS, creditPaise = totalCogsPaise)
                }
                ledgerService.postJournal(
                    description = "Reversal for cancelled ${invoice.invoiceNumber}: $reason",
                    referenceType = "INVOICE_CANCEL",
                    referenceId = invoiceId,
                    dateMillis = now,
                    lines = revLines,
                    businessId = bizId
                )
            }

            billingDao.updateInvoice(
                invoice.copy(
                    status = InvoiceStatus.CANCELLED,
                    balanceDuePaise = 0L,
                    notes = "${invoice.notes}\n[Cancelled: $reason]".trim(),
                    updatedAt = now
                )
            )
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "CANCEL_INVOICE",
                    entityType = "Invoice",
                    entityId = invoiceId,
                    summary = "Cancelled & reversed ${invoice.invoiceNumber}: $reason"
                )
            )
            advancedDao.insertInvoiceAuditLog(
                InvoiceAuditLog(
                    businessId = bizId,
                    invoiceId = invoiceId,
                    invoiceNumber = invoice.invoiceNumber,
                    action = "CANCELLED_REVERSED",
                    details = "Finalized invoice cancelled & stock/ledger reversed: $reason",
                    createdAt = now
                )
            )
        }
        val cancelledInv = billingDao.getInvoiceByIdOnce(invoiceId)
        val cancelledItems = billingDao.getInvoiceItemsOnce(invoiceId)
        if (cancelledInv != null) {
            cloudSyncRepository?.enqueueInvoiceSync(cancelledInv, cancelledItems)
        }
        cancelledItems.mapNotNull { it.productId }.distinct().forEach { pid ->
            inventoryDao.getProductByIdOnce(pid)?.let { cloudSyncRepository?.enqueueProductSync(it) }
        }
    }

    suspend fun duplicateAsDraft(
        sourceInvoiceId: Long,
        targetDocumentType: InvoiceDocumentType? = null
    ): Result<Long> = runCatching {
        val (orig, items) = requireNotNull(getInvoiceWithItemsOnce(sourceInvoiceId)) {
            "Source invoice not found"
        }
        val req = SaveInvoiceRequest(
            documentType = targetDocumentType ?: orig.documentType,
            saveAsDraft = true,
            customerId = orig.customerId,
            customerName = orig.customerName,
            customerPhone = orig.customerPhone,
            customerAddress = orig.customerAddress,
            customerGstin = orig.customerGstin,
            isInterState = orig.isInterState,
            items = items.map {
                InvoiceLineDraft(
                    productId = it.productId,
                    batchId = it.batchId,
                    productName = it.productName,
                    productCode = it.productCode,
                    hsnSacCode = it.hsnSacCode,
                    unitName = it.unitName,
                    quantity = Money.fromPaise(it.quantityScaled),
                    rate = Money.fromPaise(it.ratePaise),
                    purchaseCost = Money.fromPaise(it.purchaseCostPaise),
                    discountPercent = Money.fromPaise(it.discountPercentScaled),
                    taxRatePercent = Money.fromPaise(it.taxRatePercentScaled),
                    isTaxInclusive = it.isTaxInclusive,
                    batchNumber = it.batchNumber,
                    expiryDateMillis = it.expiryDateMillis,
                    serialNumber = it.serialNumber
                )
            },
            notes = orig.notes,
            termsAndConditions = orig.termsAndConditions
        )
        saveOrFinalizeInvoice(req).getOrThrow()
    }

    suspend fun logThermalPrint(invoiceId: Long, printerName: String) {
        val inv = billingDao.getInvoiceByIdOnce(invoiceId) ?: return
        advancedDao.insertInvoiceAuditLog(
            InvoiceAuditLog(
                businessId = inv.businessId,
                invoiceId = inv.id,
                invoiceNumber = inv.invoiceNumber,
                action = "THERMAL_PRINTED",
                details = "Printed thermal receipt on $printerName"
            )
        )
    }

    suspend fun savePurchase(request: SavePurchaseRequest): Result<Long> = runCatching {
        require(request.items.isNotEmpty()) { "Add at least one purchase item" }
        db.withTransaction {
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            val supplyType = if (request.isInterState) GstSupplyType.INTER_STATE else GstSupplyType.INTRA_STATE
            val lineInputs = request.items.map {
                LineCalculationInput(
                    quantity = it.quantity,
                    unitPrice = it.rate,
                    discountPercent = it.discountPercent,
                    taxRatePercent = it.taxRatePercent,
                    isTaxInclusive = false
                )
            }
            val lineResults = lineInputs.map { GstCalculator.calculateLine(it, supplyType) }
            val docRes = GstCalculator.calculateDocument(
                lines = lineInputs,
                supplyType = supplyType,
                additionalCharges = request.additionalCharges,
                enableRoundOff = false
            )

            val grandTotalPaise = Money.safeToPaise(docRes.grandTotal)
            val paidPaise = Money.safeToPaise(request.paidAmount.max(BigDecimal.ZERO)).coerceAtMost(grandTotalPaise)
            val duePaise = (grandTotalPaise - paidPaise).coerceAtLeast(0L)
            val isReturn = request.documentType == PurchaseDocumentType.PURCHASE_RETURN ||
                request.documentType == PurchaseDocumentType.DEBIT_NOTE

            val status = when {
                duePaise == 0L -> PurchaseStatus.PAID
                paidPaise > 0L -> PurchaseStatus.PARTIAL
                else -> PurchaseStatus.FINALIZED
            }

            val prefix = if (isReturn) "PR-" else "PUR-"
            val purNumber = "$prefix${System.currentTimeMillis() % 1000000}"
            val now = System.currentTimeMillis()

            val purchase = Purchase(
                businessId = bizId,
                purchaseNumber = purNumber,
                supplierBillNumber = request.supplierBillNumber,
                documentType = request.documentType,
                status = status,
                purchaseDateMillis = request.purchaseDateMillis,
                supplierId = request.supplierId,
                supplierName = request.supplierName.ifBlank { "Cash Supplier" },
                supplierGstin = request.supplierGstin,
                isInterState = request.isInterState,
                subtotalPaise = Money.safeToPaise(docRes.subtotal),
                discountPaise = Money.safeToPaise(docRes.itemDiscountTotal),
                taxableAmountPaise = Money.safeToPaise(docRes.taxableAmount),
                cgstPaise = Money.safeToPaise(docRes.cgstTotal),
                sgstPaise = Money.safeToPaise(docRes.sgstTotal),
                igstPaise = Money.safeToPaise(docRes.igstTotal),
                totalTaxPaise = Money.safeToPaise(docRes.totalTax),
                additionalChargesPaise = Money.safeToPaise(docRes.additionalCharges),
                grandTotalPaise = grandTotalPaise,
                paidAmountPaise = paidPaise,
                balanceDuePaise = duePaise,
                paymentMode = request.paymentMode,
                linkedOriginalPurchaseId = request.linkedOriginalPurchaseId,
                notes = request.notes,
                createdAt = now,
                updatedAt = now
            )

            val purchaseId = billingDao.insertPurchase(purchase)
            val pItems = request.items.mapIndexed { idx, draft ->
                val res = lineResults[idx]
                PurchaseItem(
                    purchaseId = purchaseId,
                    productId = draft.productId,
                    productName = draft.productName,
                    hsnSacCode = draft.hsnSacCode,
                    unitName = draft.unitName,
                    quantityScaled = Money.safeToPaise(draft.quantity),
                    ratePaise = Money.safeToPaise(draft.rate),
                    discountPercentScaled = Money.safeToPaise(draft.discountPercent),
                    taxRatePercentScaled = Money.safeToPaise(draft.taxRatePercent),
                    taxableAmountPaise = Money.safeToPaise(res.taxableAmount),
                    cgstPaise = Money.safeToPaise(res.cgstAmount),
                    sgstPaise = Money.safeToPaise(res.sgstAmount),
                    igstPaise = Money.safeToPaise(res.igstAmount),
                    lineTotalPaise = Money.safeToPaise(res.lineTotal),
                    batchNumber = draft.batchNumber,
                    expiryDateMillis = draft.expiryDateMillis
                )
            }
            billingDao.insertPurchaseItems(pItems)

            val defaultWh = inventoryDao.getAllWarehousesOnce(bizId).firstOrNull()?.id
            for (item in pItems) {
                val productId = item.productId ?: continue
                val product = inventoryDao.getProductByIdOnce(productId) ?: continue
                if (product.isServiceItem) continue
                val delta = if (isReturn) -item.quantityScaled else item.quantityScaled
                val newStock = product.currentStockScaled + delta
                inventoryDao.adjustProductStock(productId, delta, now)
                if (!isReturn && item.ratePaise > 0L) {
                    val purchaseChanged = product.purchasePricePaise != item.ratePaise
                    inventoryDao.updateProduct(
                        product.copy(
                            currentStockScaled = newStock,
                            purchasePricePaise = item.ratePaise,
                            batchNumber = item.batchNumber.ifBlank { product.batchNumber },
                            expiryDateMillis = item.expiryDateMillis ?: product.expiryDateMillis,
                            updatedAt = now
                        )
                    )
                    if (purchaseChanged) {
                        advancedDao.insertPriceHistory(
                            PriceHistory(
                                businessId = bizId,
                                productId = productId,
                                productName = product.name,
                                purchasePricePaise = item.ratePaise,
                                sellingPricePaise = product.sellingPricePaise,
                                wholesalePricePaise = product.wholesalePricePaise,
                                mrpPaise = product.mrpPaise,
                                discountPercentScaled = product.defaultDiscountPercentScaled,
                                supplierName = purchase.supplierName,
                                changeSource = "PURCHASE",
                                changedAtMillis = now
                            )
                        )
                    }
                }
                if (!isReturn && (item.batchNumber.isNotBlank() || item.expiryDateMillis != null)) {
                    val bNo = item.batchNumber.ifBlank { "PUR-$purNumber" }
                    val existingBatch = advancedDao.findBatchByProductAndNumber(productId, bNo)
                    if (existingBatch != null) {
                        advancedDao.adjustBatchQuantity(existingBatch.id, item.quantityScaled)
                    } else {
                        advancedDao.insertBatch(
                            ProductBatch(
                                businessId = bizId,
                                productId = productId,
                                supplierId = purchase.supplierId,
                                supplierName = purchase.supplierName,
                                batchNumber = bNo,
                                expiryDateMillis = item.expiryDateMillis,
                                purchaseDateMillis = purchase.purchaseDateMillis,
                                initialQuantityScaled = item.quantityScaled,
                                remainingQuantityScaled = item.quantityScaled,
                                unitCostPaise = item.ratePaise,
                                purchaseId = purchaseId,
                                createdAt = now
                            )
                        )
                    }
                }
                inventoryDao.insertStockMovement(
                    StockMovement(
                        businessId = bizId,
                        productId = productId,
                        warehouseId = defaultWh,
                        movementType = if (isReturn) StockMovementType.PURCHASE_RETURN_OUT else StockMovementType.PURCHASE_IN,
                        quantityDeltaScaled = delta,
                        resultingStockScaled = newStock,
                        unitCostPaise = item.ratePaise,
                        referenceType = "PURCHASE",
                        referenceId = purchaseId,
                        batchNumber = item.batchNumber,
                        notes = "${purchase.documentType.label} $purNumber",
                        createdAt = now
                    )
                )
            }

            if (purchase.supplierId != null && duePaise > 0L) {
                val delta = if (isReturn) -duePaise else duePaise
                partyDao.adjustSupplierBalance(purchase.supplierId, delta, now)
            }

            if (paidPaise > 0L) {
                val settleAcc = ledgerService.resolveAccountForPaymentMode(purchase.paymentMode, businessId = bizId)
                val pId = accountingDao.insertPayment(
                    Payment(
                        businessId = bizId,
                        receiptNumber = "PAY-$purNumber",
                        direction = if (isReturn) PaymentDirection.REFUND_RECEIVED else PaymentDirection.PAID,
                        paymentMode = purchase.paymentMode,
                        amountPaise = paidPaise,
                        paymentDateMillis = purchase.purchaseDateMillis,
                        supplierId = purchase.supplierId,
                        partyName = purchase.supplierName,
                        accountId = settleAcc.id,
                        notes = "Payment for $purNumber",
                        createdAt = now
                    )
                )
                accountingDao.insertPaymentAllocation(
                    PaymentAllocation(paymentId = pId, purchaseId = purchaseId, allocatedAmountPaise = paidPaise)
                )
            }

            val settleCode = if (purchase.paymentMode == PaymentMode.CASH) {
                HisabPulseDatabase.Companion.AccountCodes.CASH
            } else {
                HisabPulseDatabase.Companion.AccountCodes.BANK
            }
            val inventoryPortion = (grandTotalPaise - purchase.totalTaxPaise).coerceAtLeast(0L)
            val journalLines = if (!isReturn) {
                buildList {
                    if (inventoryPortion > 0L) add(LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.INVENTORY, debitPaise = inventoryPortion))
                    if (purchase.totalTaxPaise > 0L) add(LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.INPUT_GST, debitPaise = purchase.totalTaxPaise))
                    if (paidPaise > 0L) add(LedgerPostingLine(settleCode, creditPaise = paidPaise))
                    if (duePaise > 0L) add(LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.PAYABLES, creditPaise = duePaise, partyType = if (purchase.supplierId != null) "SUPPLIER" else "NONE", partyId = purchase.supplierId))
                }
            } else {
                buildList {
                    if (paidPaise > 0L) add(LedgerPostingLine(settleCode, debitPaise = paidPaise))
                    if (duePaise > 0L) add(LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.PAYABLES, debitPaise = duePaise, partyType = if (purchase.supplierId != null) "SUPPLIER" else "NONE", partyId = purchase.supplierId))
                    if (inventoryPortion > 0L) add(LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.INVENTORY, creditPaise = inventoryPortion))
                    if (purchase.totalTaxPaise > 0L) add(LedgerPostingLine(HisabPulseDatabase.Companion.AccountCodes.INPUT_GST, creditPaise = purchase.totalTaxPaise))
                }
            }

            ledgerService.postJournal(
                description = "${purchase.documentType.label} $purNumber (${purchase.supplierName})",
                referenceType = "PURCHASE",
                referenceId = purchaseId,
                dateMillis = purchase.purchaseDateMillis,
                lines = journalLines,
                businessId = bizId
            )

            coreDao.insertAuditLog(
                AuditLog(
                    businessId = bizId,
                    action = "CREATE_PURCHASE",
                    entityType = "Purchase",
                    entityId = purchaseId,
                    summary = "Recorded ${purchase.documentType.label} $purNumber for ₹${Money.fromPaise(grandTotalPaise)}"
                )
            )
            purchaseId
        }.also {
            request.items.mapNotNull { it.productId }.distinct().forEach { pid ->
                inventoryDao.getProductByIdOnce(pid)?.let { prod -> cloudSyncRepository?.enqueueProductSync(prod) }
            }
        }
    }
}
