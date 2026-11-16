package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.BusinessHealthMetric
import com.example.data.local.entities.ExpiryAlert
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PriceHistory
import com.example.data.local.entities.Product
import com.example.data.local.entities.ProductBatch
import com.example.data.local.entities.ReorderSuggestion
import com.example.data.local.entities.StockMovementType
import com.example.data.local.entities.StockPrediction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StockPredictionItem(
    val product: Product,
    val avgDailySales7dScaled: Long,
    val avgDailySales30dScaled: Long,
    val avgDailySales90dScaled: Long,
    val saleEventCount: Int,
    val estimatedDaysRemaining: Int, // -1 if insufficient data or zero sales
    val suggestedReorderScaled: Long,
    val manualOverrideScaled: Long?,
    val velocityClass: String, // FAST, NORMAL, SLOW, OVERSTOCK, INSUFFICIENT_DATA
    val explanation: String
)

data class ProductProfitRow(
    val productId: Long?,
    val productName: String,
    val categoryName: String,
    val qtySoldScaled: Long,
    val revenuePaise: Long,
    val cogsPaise: Long,
    val discountPaise: Long,
    val grossProfitPaise: Long,
    val marginPercent: Double
)

data class InvoiceProfitRow(
    val invoiceId: Long,
    val invoiceNumber: String,
    val customerName: String,
    val dateMillis: Long,
    val revenuePaise: Long,
    val cogsPaise: Long,
    val discountPaise: Long,
    val profitPaise: Long,
    val isLossMaking: Boolean
)

data class PeriodProfitRow(
    val periodLabel: String,
    val revenuePaise: Long,
    val cogsPaise: Long,
    val expensePaise: Long,
    val netProfitPaise: Long
)

data class ProfitIntelligenceSummary(
    val totalRevenuePaise: Long,
    val totalCogsPaise: Long,
    val totalDiscountsPaise: Long,
    val grossProfitPaise: Long,
    val operatingExpensesPaise: Long,
    val netProfitPaise: Long,
    val grossMarginPercent: Double,
    val netMarginPercent: Double,
    val byProduct: List<ProductProfitRow>,
    val byCategory: List<ProductProfitRow>,
    val byInvoice: List<InvoiceProfitRow>,
    val dailyProfits: List<PeriodProfitRow>,
    val monthlyProfits: List<PeriodProfitRow>,
    val lossMakingInvoices: List<InvoiceProfitRow>,
    val topProfitableProducts: List<ProductProfitRow>
)

@Serializable
data class HealthSubMetric(
    val name: String,
    val score: Int, // 0..100
    val weight: Int,
    val hasEnoughData: Boolean,
    val valueText: String,
    val statusLabel: String
)

data class BusinessHealthSummary(
    val overallScore: Int,
    val hasMinimumData: Boolean,
    val subMetrics: List<HealthSubMetric>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
class IntelligenceRepository(private val db: HisabPulseDatabase) {
    private val coreDao = db.coreDao()
    private val inventoryDao = db.inventoryDao()
    private val billingDao = db.billingDao()
    private val accountingDao = db.accountingDao()
    private val partyDao = db.partyDao()
    private val advancedDao = db.advancedDao()

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val priceHistory: Flow<List<PriceHistory>> = activeBizFlow.flatMapLatest { advancedDao.observeAllPriceHistory(it) }
    val productBatches: Flow<List<ProductBatch>> = activeBizFlow.flatMapLatest { advancedDao.observeAllBatches(it) }
    val expiryAlerts: Flow<List<ExpiryAlert>> = activeBizFlow.flatMapLatest { advancedDao.observeExpiryAlerts(it) }
    val healthHistory: Flow<List<BusinessHealthMetric>> = activeBizFlow.flatMapLatest { advancedDao.observeBusinessHealthMetrics(it) }
    val reorderSuggestions: Flow<List<ReorderSuggestion>> = activeBizFlow.flatMapLatest { advancedDao.observeReorderSuggestions(it) }

    fun observePriceHistoryForProduct(productId: Long): Flow<List<PriceHistory>> =
        advancedDao.observePriceHistoryForProduct(productId)

    fun observeBatchesForProduct(productId: Long): Flow<List<ProductBatch>> =
        advancedDao.observeAvailableBatchesForProduct(productId)

    suspend fun computeStockPredictions(leadTimeDays: Int = 7, safetyDays: Int = 5): List<StockPredictionItem> {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val products = inventoryDao.getAllProductsOnce(bizId).filter { it.isActive && !it.isServiceItem }
        val invoices = billingDao.getAllInvoicesOnce(bizId)
            .filter {
                it.status != InvoiceStatus.DRAFT &&
                    it.status != InvoiceStatus.HELD_POS &&
                    it.status != InvoiceStatus.CANCELLED &&
                    it.documentType.affectsLedger &&
                    it.documentType != InvoiceDocumentType.SALES_RETURN &&
                    it.documentType != InvoiceDocumentType.CREDIT_NOTE
            }
        val invoiceDateMap = invoices.associate { it.id to it.invoiceDateMillis }
        val allItems = billingDao.getAllInvoiceItemsOnce(bizId).filter { it.invoiceId in invoiceDateMap }
        val savedOverrides = advancedDao.getStockPredictionsOnce(bizId).associateBy { it.productId }

        val now = System.currentTimeMillis()
        val ms7 = 7 * 86_400_000L
        val ms30 = 30 * 86_400_000L
        val ms90 = 90 * 86_400_000L

        val results = mutableListOf<StockPredictionItem>()
        for (p in products) {
            val pItems = allItems.filter { it.productId == p.id }
            val distinctInvoices = pItems.map { it.invoiceId }.distinct().size
            val q7 = pItems.filter { (invoiceDateMap[it.invoiceId] ?: 0L) >= now - ms7 }.sumOf { it.quantityScaled }
            val q30 = pItems.filter { (invoiceDateMap[it.invoiceId] ?: 0L) >= now - ms30 }.sumOf { it.quantityScaled }
            val q90 = pItems.filter { (invoiceDateMap[it.invoiceId] ?: 0L) >= now - ms90 }.sumOf { it.quantityScaled }

            val avg7 = q7 / 7L
            val avg30 = q30 / 30L
            val avg90 = q90 / 90L
            val overrideVal = savedOverrides[p.id]?.manualReorderOverrideScaled

            if (distinctInvoices < 3 || q90 <= 0L) {
                val reason = if (distinctInvoices == 0) {
                    "Insufficient sales history (0 finalized sales)"
                } else {
                    "Insufficient sales history ($distinctInvoices/3 finalized sales required)"
                }
                val fallbackReorder = if (p.currentStockScaled <= p.minStockScaled) {
                    (p.minStockScaled * 2 - p.currentStockScaled).coerceAtLeast(0L)
                } else 0L
                val item = StockPredictionItem(
                    product = p,
                    avgDailySales7dScaled = avg7,
                    avgDailySales30dScaled = avg30,
                    avgDailySales90dScaled = avg90,
                    saleEventCount = distinctInvoices,
                    estimatedDaysRemaining = -1,
                    suggestedReorderScaled = overrideVal ?: fallbackReorder,
                    manualOverrideScaled = overrideVal,
                    velocityClass = "INSUFFICIENT_DATA",
                    explanation = reason
                )
                results += item
                advancedDao.upsertStockPrediction(
                    StockPrediction(
                        id = savedOverrides[p.id]?.id ?: 0L,
                        businessId = bizId,
                        productId = p.id,
                        avgDailySalesScaled = avg30,
                        estimatedDaysRemaining = -1,
                        suggestedReorderScaled = item.suggestedReorderScaled,
                        manualReorderOverrideScaled = overrideVal,
                        velocityClass = "INSUFFICIENT_DATA",
                        lastUpdatedMillis = now
                    )
                )
                continue
            }

            // Weighted blend prioritizing 30d velocity when available
            val effectiveDaily = when {
                avg30 > 0L -> (avg7 * 3 + avg30 * 5 + avg90 * 2) / 10L
                avg90 > 0L -> avg90
                else -> avg7
            }.coerceAtLeast(1L)

            val daysRem = (p.currentStockScaled / effectiveDaily).toInt()
            val targetCoverScaled = effectiveDaily * (leadTimeDays + safetyDays + 15)
            val autoReorder = (targetCoverScaled - p.currentStockScaled).coerceAtLeast(0L)
            val velocityClass = when {
                daysRem > 90 && p.currentStockScaled > p.minStockScaled * 3 -> "OVERSTOCK"
                effectiveDaily >= 300L || daysRem <= 10 -> "FAST"
                effectiveDaily >= 50L -> "NORMAL"
                else -> "SLOW"
            }
            val explanation = when (velocityClass) {
                "FAST" -> "Fast-moving: ~${Money.formatQty(Money.fromPaise(effectiveDaily))}/day (${daysRem}d stock left)"
                "OVERSTOCK" -> "Overstock: >90d cover at current velocity"
                "SLOW" -> "Slow-moving: ~${Money.formatQty(Money.fromPaise(effectiveDaily))}/day"
                else -> "Steady velocity: ~${Money.formatQty(Money.fromPaise(effectiveDaily))}/day (${daysRem}d left)"
            }

            val item = StockPredictionItem(
                product = p,
                avgDailySales7dScaled = avg7,
                avgDailySales30dScaled = avg30,
                avgDailySales90dScaled = avg90,
                saleEventCount = distinctInvoices,
                estimatedDaysRemaining = daysRem,
                suggestedReorderScaled = overrideVal ?: autoReorder,
                manualOverrideScaled = overrideVal,
                velocityClass = velocityClass,
                explanation = explanation
            )
            results += item
            advancedDao.upsertStockPrediction(
                StockPrediction(
                    id = savedOverrides[p.id]?.id ?: 0L,
                    businessId = bizId,
                    productId = p.id,
                    avgDailySalesScaled = effectiveDaily,
                    estimatedDaysRemaining = daysRem,
                    suggestedReorderScaled = autoReorder,
                    manualReorderOverrideScaled = overrideVal,
                    velocityClass = velocityClass,
                    lastUpdatedMillis = now
                )
            )
        }
        return results.sortedWith(compareBy<StockPredictionItem> {
            when (it.velocityClass) {
                "FAST" -> 0
                "NORMAL" -> 1
                "SLOW" -> 2
                "OVERSTOCK" -> 3
                else -> 4
            }
        }.thenBy { if (it.estimatedDaysRemaining < 0) Int.MAX_VALUE else it.estimatedDaysRemaining })
    }

    suspend fun setManualReorderOverride(productId: Long, customReorderScaled: Long?) {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val existing = advancedDao.getStockPredictionsOnce(bizId).firstOrNull { it.productId == productId }
        if (existing != null) {
            advancedDao.upsertStockPrediction(existing.copy(manualReorderOverrideScaled = customReorderScaled))
        }
        val prod = inventoryDao.getProductByIdOnce(productId)
        if (prod != null && customReorderScaled != null) {
            advancedDao.insertReorderSuggestion(
                ReorderSuggestion(
                    businessId = bizId,
                    productId = productId,
                    productName = prod.name,
                    suggestedQtyScaled = existing?.suggestedReorderScaled ?: customReorderScaled,
                    customQtyScaled = customReorderScaled,
                    notes = "Manual override saved"
                )
            )
        }
    }

    suspend fun computeProfitIntelligence(
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE
    ): ProfitIntelligenceSummary {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val productsMap = inventoryDao.getAllProductsOnce(bizId).associateBy { it.id }
        val invoices = billingDao.getAllInvoicesOnce(bizId)
            .filter {
                it.status != InvoiceStatus.DRAFT &&
                    it.status != InvoiceStatus.HELD_POS &&
                    it.status != InvoiceStatus.CANCELLED &&
                    it.documentType.affectsLedger &&
                    it.invoiceDateMillis in startMillis..endMillis
            }
        val allItems = billingDao.getAllInvoiceItemsOnce(bizId).groupBy { it.invoiceId }
        val expenses = accountingDao.getAllExpensesOnce(bizId)
            .filter { it.expenseDateMillis in startMillis..endMillis }

        val dayFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())

        var totalRevenue = 0L
        var totalCogs = 0L
        var totalDiscounts = 0L

        val productAgg = mutableMapOf<String, ProductProfitRow>()
        val categoryAgg = mutableMapOf<String, ProductProfitRow>()
        val invoiceRows = mutableListOf<InvoiceProfitRow>()
        val dailyMap = linkedMapOf<String, Triple<Long, Long, Long>>() // rev, cogs, exp
        val monthlyMap = linkedMapOf<String, Triple<Long, Long, Long>>()

        for (inv in invoices) {
            val sign = if (inv.documentType == InvoiceDocumentType.SALES_RETURN ||
                inv.documentType == InvoiceDocumentType.CREDIT_NOTE
            ) -1L else 1L

            val invRevenue = sign * (inv.grandTotalPaise - inv.totalTaxPaise).coerceAtLeast(0L)
            val invDiscount = sign * (inv.itemDiscountPaise + inv.invoiceDiscountFlatPaise)
            val items = allItems[inv.id].orEmpty()

            var invCogs = 0L
            for (item in items) {
                val prod = item.productId?.let { productsMap[it] }
                val unitCost = if (item.purchaseCostPaise > 0L) item.purchaseCostPaise else (prod?.purchasePricePaise ?: 0L)
                val lineCogs = if (prod?.isServiceItem == true) 0L else {
                    sign * InventoryRepository.computeStockValuePaise(item.quantityScaled, unitCost)
                }
                val lineNetRev = sign * item.taxableAmountPaise
                val lineDisc = sign * item.discountAmountPaise
                invCogs += lineCogs

                val pKey = "${item.productId ?: 0}_${item.productName}"
                val prevP = productAgg[pKey]
                val catName = prod?.categoryName ?: "General"
                val newPRev = (prevP?.revenuePaise ?: 0L) + lineNetRev
                val newPCogs = (prevP?.cogsPaise ?: 0L) + lineCogs
                val newPGross = newPRev - newPCogs
                val newPMargin = if (newPRev > 0L) (newPGross * 100.0) / newPRev else 0.0
                productAgg[pKey] = ProductProfitRow(
                    productId = item.productId,
                    productName = item.productName,
                    categoryName = catName,
                    qtySoldScaled = (prevP?.qtySoldScaled ?: 0L) + sign * item.quantityScaled,
                    revenuePaise = newPRev,
                    cogsPaise = newPCogs,
                    discountPaise = (prevP?.discountPaise ?: 0L) + lineDisc,
                    grossProfitPaise = newPGross,
                    marginPercent = newPMargin
                )

                val prevC = categoryAgg[catName]
                val newCRev = (prevC?.revenuePaise ?: 0L) + lineNetRev
                val newCCogs = (prevC?.cogsPaise ?: 0L) + lineCogs
                val newCGross = newCRev - newCCogs
                val newCMargin = if (newCRev > 0L) (newCGross * 100.0) / newCRev else 0.0
                categoryAgg[catName] = ProductProfitRow(
                    productId = null,
                    productName = catName,
                    categoryName = catName,
                    qtySoldScaled = (prevC?.qtySoldScaled ?: 0L) + sign * item.quantityScaled,
                    revenuePaise = newCRev,
                    cogsPaise = newCCogs,
                    discountPaise = (prevC?.discountPaise ?: 0L) + lineDisc,
                    grossProfitPaise = newCGross,
                    marginPercent = newCMargin
                )
            }

            val invProfit = invRevenue - invCogs
            totalRevenue += invRevenue
            totalCogs += invCogs
            totalDiscounts += invDiscount

            invoiceRows += InvoiceProfitRow(
                invoiceId = inv.id,
                invoiceNumber = inv.invoiceNumber,
                customerName = inv.customerName,
                dateMillis = inv.invoiceDateMillis,
                revenuePaise = invRevenue,
                cogsPaise = invCogs,
                discountPaise = invDiscount,
                profitPaise = invProfit,
                isLossMaking = invProfit < 0L
            )

            val dKey = dayFmt.format(Date(inv.invoiceDateMillis))
            val dCur = dailyMap[dKey] ?: Triple(0L, 0L, 0L)
            dailyMap[dKey] = Triple(dCur.first + invRevenue, dCur.second + invCogs, dCur.third)

            val mKey = monthFmt.format(Date(inv.invoiceDateMillis))
            val mCur = monthlyMap[mKey] ?: Triple(0L, 0L, 0L)
            monthlyMap[mKey] = Triple(mCur.first + invRevenue, mCur.second + invCogs, mCur.third)
        }

        var totalExpenses = 0L
        for (exp in expenses) {
            totalExpenses += exp.amountPaise
            val dKey = dayFmt.format(Date(exp.expenseDateMillis))
            val dCur = dailyMap[dKey] ?: Triple(0L, 0L, 0L)
            dailyMap[dKey] = Triple(dCur.first, dCur.second, dCur.third + exp.amountPaise)

            val mKey = monthFmt.format(Date(exp.expenseDateMillis))
            val mCur = monthlyMap[mKey] ?: Triple(0L, 0L, 0L)
            monthlyMap[mKey] = Triple(mCur.first, mCur.second, mCur.third + exp.amountPaise)
        }

        val grossProfit = totalRevenue - totalCogs
        val netProfit = grossProfit - totalExpenses
        val grossMargin = if (totalRevenue > 0L) (grossProfit * 100.0) / totalRevenue else 0.0
        val netMargin = if (totalRevenue > 0L) (netProfit * 100.0) / totalRevenue else 0.0

        val byProdList = productAgg.values.sortedByDescending { it.grossProfitPaise }
        return ProfitIntelligenceSummary(
            totalRevenuePaise = totalRevenue,
            totalCogsPaise = totalCogs,
            totalDiscountsPaise = totalDiscounts,
            grossProfitPaise = grossProfit,
            operatingExpensesPaise = totalExpenses,
            netProfitPaise = netProfit,
            grossMarginPercent = grossMargin,
            netMarginPercent = netMargin,
            byProduct = byProdList,
            byCategory = categoryAgg.values.sortedByDescending { it.grossProfitPaise },
            byInvoice = invoiceRows.sortedByDescending { it.dateMillis },
            dailyProfits = dailyMap.map { (k, v) ->
                PeriodProfitRow(k, v.first, v.second, v.third, (v.first - v.second) - v.third)
            },
            monthlyProfits = monthlyMap.map { (k, v) ->
                PeriodProfitRow(k, v.first, v.second, v.third, (v.first - v.second) - v.third)
            },
            lossMakingInvoices = invoiceRows.filter { it.isLossMaking },
            topProfitableProducts = byProdList.take(5)
        )
    }

    suspend fun computeAndSaveBusinessHealth(): BusinessHealthSummary {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val now = System.currentTimeMillis()
        val ms30 = 30 * 86_400_000L

        val profitSummary = computeProfitIntelligence()
        val invoices = billingDao.getAllInvoicesOnce(bizId)
            .filter { it.status != InvoiceStatus.DRAFT && it.status != InvoiceStatus.HELD_POS && it.status != InvoiceStatus.CANCELLED && it.documentType.affectsLedger }
        val products = inventoryDao.getAllProductsOnce(bizId).filter { it.isActive && !it.isServiceItem }
        val customers = partyDao.getAllCustomersOnce(bizId).filter { it.isActive }
        val suppliers = partyDao.getAllSuppliersOnce(bizId).filter { it.isActive }
        val accounts = accountingDao.getAllAccountsOnce(bizId)

        val current30Sales = invoices.filter { it.invoiceDateMillis >= now - ms30 }.sumOf { it.grandTotalPaise }
        val prev30Sales = invoices.filter { it.invoiceDateMillis in (now - 2 * ms30) until (now - ms30) }.sumOf { it.grandTotalPaise }
        val hasGrowthBaseline = prev30Sales > 0L
        val growthPct = if (hasGrowthBaseline) ((current30Sales - prev30Sales) * 100.0) / prev30Sales else 0.0
        val growthScore = when {
            !hasGrowthBaseline -> 50
            growthPct >= 15.0 -> 95
            growthPct >= 0.0 -> (70 + growthPct).toInt().coerceAtMost(94)
            else -> (65 + growthPct).toInt().coerceIn(20, 69)
        }

        val hasSales = profitSummary.totalRevenuePaise > 0L
        val marginScore = if (!hasSales) 0 else when {
            profitSummary.grossMarginPercent >= 30.0 -> 95
            profitSummary.grossMarginPercent >= 18.0 -> 80
            profitSummary.grossMarginPercent >= 8.0 -> 60
            else -> 30
        }

        val expRatio = if (hasSales) (profitSummary.operatingExpensesPaise * 100.0) / profitSummary.totalRevenuePaise else 0.0
        val expScore = if (!hasSales) 50 else when {
            expRatio <= 15.0 -> 92
            expRatio <= 30.0 -> 75
            expRatio <= 50.0 -> 55
            else -> 25
        }

        val totalSalesBilled = invoices.sumOf { it.grandTotalPaise }.coerceAtLeast(1L)
        val totalReceivables = customers.sumOf { it.currentBalancePaise.coerceAtLeast(0L) }
        val totalPayables = suppliers.sumOf { it.currentBalancePaise.coerceAtLeast(0L) }
        val recRatio = if (invoices.isNotEmpty()) (totalReceivables * 100.0) / totalSalesBilled else 0.0
        val recScore = if (invoices.isEmpty()) 50 else when {
            recRatio <= 15.0 -> 92
            recRatio <= 35.0 -> 72
            else -> 40
        }

        val cashAndBank = accounts.filter {
            it.code == HisabPulseDatabase.Companion.AccountCodes.CASH ||
                it.isBankAccount ||
                it.code == HisabPulseDatabase.Companion.AccountCodes.BANK
        }.sumOf { it.currentBalancePaise }
        val payScore = if (totalPayables == 0L) 90 else {
            val cover = cashAndBank.toDouble() / totalPayables.toDouble()
            when {
                cover >= 1.5 -> 92
                cover >= 0.8 -> 72
                else -> 40
            }
        }

        val invValue = products.sumOf { InventoryRepository.computeStockValuePaise(it.currentStockScaled, it.purchasePricePaise) }
        val invTurnover = if (invValue > 0L && profitSummary.totalCogsPaise > 0L) {
            profitSummary.totalCogsPaise.toDouble() / invValue.toDouble()
        } else 0.0
        val turnoverScore = if (products.isEmpty() || profitSummary.totalCogsPaise == 0L) 50 else when {
            invTurnover >= 2.0 -> 90
            invTurnover >= 0.5 -> 72
            else -> 45
        }

        val lowOrOut = products.count { it.currentStockScaled <= it.minStockScaled }
        val lowStockScore = if (products.isEmpty()) 50 else {
            val healthyPct = ((products.size - lowOrOut) * 100) / products.size
            healthyPct.coerceIn(15, 100)
        }

        val predictions = computeStockPredictions()
        val slowCount = predictions.count { it.velocityClass == "SLOW" || it.velocityClass == "OVERSTOCK" }
        val slowScore = if (predictions.none { it.velocityClass != "INSUFFICIENT_DATA" }) 50 else {
            (((predictions.size - slowCount) * 100) / predictions.size.coerceAtLeast(1)).coerceIn(20, 100)
        }

        val totalCollected = invoices.sumOf { it.paidAmountPaise }
        val collectionRate = if (invoices.isNotEmpty()) (totalCollected * 100.0) / totalSalesBilled else 0.0
        val collectionScore = if (invoices.isEmpty()) 50 else collectionRate.toInt().coerceIn(20, 100)

        val cashFlowScore = when {
            accounts.isEmpty() -> 50
            cashAndBank > 0L && profitSummary.netProfitPaise >= 0L -> 90
            cashAndBank > 0L -> 70
            else -> 30
        }

        val metrics = listOf(
            HealthSubMetric(
                "Sales Growth (30d)", growthScore, 12, hasGrowthBaseline,
                if (hasGrowthBaseline) "${"%.1f".format(growthPct)}%" else "Insufficient data",
                if (!hasGrowthBaseline) "Insufficient prior period data" else if (growthPct >= 0) "Growing" else "Declining"
            ),
            HealthSubMetric(
                "Gross Margin", marginScore, 14, hasSales,
                if (hasSales) "${"%.1f".format(profitSummary.grossMarginPercent)}%" else "No sales",
                if (!hasSales) "Insufficient data" else if (profitSummary.grossMarginPercent >= 18) "Healthy" else "Tight Margin"
            ),
            HealthSubMetric(
                "Expense Ratio", expScore, 10, hasSales,
                if (hasSales) "${"%.1f".format(expRatio)}% of sales" else "No sales",
                if (!hasSales) "Insufficient data" else if (expRatio <= 30) "Controlled" else "High Expenses"
            ),
            HealthSubMetric(
                "Receivables Health", recScore, 10, invoices.isNotEmpty(),
                "₹${Money.formatPlain(Money.fromPaise(totalReceivables))}",
                if (invoices.isEmpty()) "No invoices" else if (recRatio <= 35) "Manageable" else "High Pending Dues"
            ),
            HealthSubMetric(
                "Payables Coverage", payScore, 8, suppliers.isNotEmpty() || totalPayables > 0L,
                "₹${Money.formatPlain(Money.fromPaise(totalPayables))}",
                if (totalPayables == 0L) "Debt Free" else if (payScore >= 70) "Covered" else "Watch Cash"
            ),
            HealthSubMetric(
                "Inventory Turnover", turnoverScore, 10, products.isNotEmpty() && profitSummary.totalCogsPaise > 0L,
                if (invValue > 0L) "${"%.2f".format(invTurnover)}x" else "Insufficient data",
                if (profitSummary.totalCogsPaise == 0L) "Insufficient sales data" else if (turnoverScore >= 70) "Active" else "Slow Turnover"
            ),
            HealthSubMetric(
                "Low-Stock Health", lowStockScore, 10, products.isNotEmpty(),
                "${products.size - lowOrOut}/${products.size} stocked",
                if (products.isEmpty()) "No items" else if (lowOrOut == 0) "Optimal" else "$lowOrOut Low/Out"
            ),
            HealthSubMetric(
                "Slow/Dead Stock", slowScore, 8, predictions.any { it.velocityClass != "INSUFFICIENT_DATA" },
                "$slowCount slow/overstock",
                if (predictions.none { it.velocityClass != "INSUFFICIENT_DATA" }) "Insufficient history" else if (slowCount == 0) "Clean" else "Review Stock"
            ),
            HealthSubMetric(
                "Collection Rate", collectionScore, 10, invoices.isNotEmpty(),
                if (invoices.isNotEmpty()) "${"%.1f".format(collectionRate)}%" else "No invoices",
                if (invoices.isEmpty()) "No invoices" else if (collectionRate >= 75) "Strong" else "Follow Up"
            ),
            HealthSubMetric(
                "Cash & Liquidity", cashFlowScore, 8, accounts.isNotEmpty(),
                "₹${Money.formatPlain(Money.fromPaise(cashAndBank))}",
                if (cashAndBank > 0L) "Positive" else "Low Liquidity"
            )
        )

        val totalWeight = metrics.sumOf { it.weight }.coerceAtLeast(1)
        val weightedScore = metrics.sumOf { it.score * it.weight } / totalWeight
        val hasMinData = invoices.isNotEmpty() || products.isNotEmpty()

        val strengths = metrics.filter { it.hasEnoughData && it.score >= 75 }.map { "${it.name}: ${it.valueText} (${it.statusLabel})" }
        val weaknesses = metrics.filter { it.hasEnoughData && it.score < 60 }.map { "${it.name}: ${it.valueText} (${it.statusLabel})" }
        val recommendations = buildList {
            if (!hasMinData) add("Record your first sales, purchases, and expenses to unlock live business health insights.")
            if (lowOrOut > 0) add("Restock $lowOrOut low/out-of-stock products in Smart Inventory.")
            if (totalReceivables > 0L && recRatio > 25.0) add("Send payment reminders to customers with pending dues (₹${Money.formatPlain(Money.fromPaise(totalReceivables))}).")
            if (profitSummary.lossMakingInvoices.isNotEmpty()) add("Review ${profitSummary.lossMakingInvoices.size} loss-making invoice(s) in Profit Intelligence.")
            if (expRatio > 30.0) add("Operating expenses are ${"%.1f".format(expRatio)}% of revenue — audit recurring overhead.")
            if (isEmpty()) add("Maintain current margins, timely collections, and weekly SAF backups.")
        }

        val jsonMetrics = Json.encodeToString(metrics)
        advancedDao.insertBusinessHealthMetric(
            BusinessHealthMetric(
                businessId = bizId,
                overallScore = weightedScore,
                metricDetailsJson = jsonMetrics,
                strengthsSummary = strengths.joinToString("; "),
                weaknessesSummary = weaknesses.joinToString("; "),
                recordedAtMillis = now
            )
        )

        return BusinessHealthSummary(
            overallScore = weightedScore,
            hasMinimumData = hasMinData,
            subMetrics = metrics,
            strengths = strengths,
            weaknesses = weaknesses,
            recommendations = recommendations
        )
    }

    suspend fun addProductBatch(
        productId: Long,
        batchNumber: String,
        expiryDateMillis: Long?,
        manufacturingDateMillis: Long?,
        quantityScaled: Long,
        unitCostPaise: Long,
        supplierName: String = ""
    ): Result<Long> = runCatching {
        require(batchNumber.isNotBlank()) { "Batch number is required" }
        require(quantityScaled > 0L) { "Batch quantity must be positive" }
        db.withTransaction {
            val product = requireNotNull(inventoryDao.getProductByIdOnce(productId)) { "Product not found" }
            val now = System.currentTimeMillis()
            val batchId = advancedDao.insertBatch(
                ProductBatch(
                    businessId = product.businessId,
                    productId = productId,
                    supplierName = supplierName,
                    batchNumber = batchNumber.trim(),
                    manufacturingDateMillis = manufacturingDateMillis,
                    expiryDateMillis = expiryDateMillis,
                    purchaseDateMillis = now,
                    initialQuantityScaled = quantityScaled,
                    remainingQuantityScaled = quantityScaled,
                    unitCostPaise = unitCostPaise,
                    createdAt = now
                )
            )
            val newStock = product.currentStockScaled + quantityScaled
            inventoryDao.updateProduct(
                product.copy(
                    currentStockScaled = newStock,
                    batchNumber = batchNumber.trim(),
                    expiryDateMillis = expiryDateMillis ?: product.expiryDateMillis,
                    trackExpiry = true,
                    updatedAt = now
                )
            )
            inventoryDao.insertStockMovement(
                com.example.data.local.entities.StockMovement(
                    businessId = product.businessId,
                    productId = productId,
                    movementType = StockMovementType.ADJUSTMENT_IN,
                    quantityDeltaScaled = quantityScaled,
                    resultingStockScaled = newStock,
                    unitCostPaise = unitCostPaise,
                    referenceType = "BATCH_ADD",
                    referenceId = batchId,
                    batchNumber = batchNumber.trim(),
                    notes = "Added batch ${batchNumber.trim()}",
                    createdAt = now
                )
            )
            refreshExpiryAlertsForBusiness(product.businessId)
            batchId
        }
    }

    suspend fun refreshExpiryAlerts(): List<ExpiryAlert> {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        return refreshExpiryAlertsForBusiness(bizId)
    }

    private suspend fun refreshExpiryAlertsForBusiness(bizId: Long): List<ExpiryAlert> {
        val now = System.currentTimeMillis()
        val batches = advancedDao.observeAllBatches(bizId)
        val products = inventoryDao.getAllProductsOnce(bizId).associateBy { it.id }
        val allBatches = batches.firstOrNull().orEmpty()
        val created = mutableListOf<ExpiryAlert>()
        for (b in allBatches) {
            val exp = b.expiryDateMillis ?: continue
            if (b.remainingQuantityScaled <= 0L) continue
            val days = (exp - now) / 86_400_000L
            val level = when {
                exp < now -> "EXPIRED"
                days <= 7L -> "WITHIN_7D"
                days <= 30L -> "WITHIN_30D"
                days <= 60L -> "WITHIN_60D"
                else -> null
            } ?: continue
            val alert = ExpiryAlert(
                businessId = bizId,
                batchId = b.id,
                productId = b.productId,
                productName = products[b.productId]?.name ?: "Item #${b.productId}",
                batchNumber = b.batchNumber,
                expiryDateMillis = exp,
                alertLevel = level
            )
            advancedDao.insertExpiryAlert(alert)
            created += alert
        }
        return created
    }
}
