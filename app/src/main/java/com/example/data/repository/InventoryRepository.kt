package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.PriceHistory
import com.example.data.local.entities.Product
import com.example.data.local.entities.ProductBatch
import com.example.data.local.entities.StockMovement
import com.example.data.local.entities.StockMovementType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryRepository(
    private val db: HisabPulseDatabase,
    private val cloudSyncRepository: CloudSyncRepository? = null
) {
    private val inventoryDao = db.inventoryDao()
    private val coreDao = db.coreDao()
    private val advancedDao = db.advancedDao()
    private val ledgerService = DoubleEntryLedgerService(db.accountingDao())

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    val activeProducts: Flow<List<Product>> = activeBizFlow.flatMapLatest { inventoryDao.observeActiveProducts(it) }
    val lowStockProducts: Flow<List<Product>> = activeBizFlow.flatMapLatest { inventoryDao.observeLowStockProducts(it) }
    val outOfStockProducts: Flow<List<Product>> = activeBizFlow.flatMapLatest { inventoryDao.observeOutOfStockProducts(it) }
    val recentMovements: Flow<List<StockMovement>> = activeBizFlow.flatMapLatest { inventoryDao.observeRecentMovements(it) }

    fun observeProduct(productId: Long): Flow<Product?> = inventoryDao.observeProductById(productId)
    fun observeProductMovements(productId: Long): Flow<List<StockMovement>> =
        inventoryDao.observeMovementsForProduct(productId)

    suspend fun findByBarcode(barcode: String): Product? {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        return inventoryDao.findProductByBarcode(barcode.trim(), bizId)
    }

    suspend fun saveProduct(product: Product): Long = db.withTransaction {
        val isNew = product.id == 0L
        val now = System.currentTimeMillis()
        val activeBiz = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        val existing = if (!isNew) inventoryDao.getProductByIdOnce(product.id) else null
        val bizId = existing?.businessId ?: activeBiz

        val toSave = if (isNew) {
            product.copy(
                businessId = bizId,
                currentStockScaled = if (product.isServiceItem) 0L else product.openingStockScaled,
                createdAt = now,
                updatedAt = now
            )
        } else {
            product.copy(
                businessId = bizId,
                currentStockScaled = existing?.currentStockScaled ?: product.currentStockScaled,
                updatedAt = now
            )
        }
        val id = inventoryDao.insertProduct(toSave)

        // Automatically track price history on new item or whenever any price/discount changes
        val priceChanged = existing == null ||
            existing.purchasePricePaise != toSave.purchasePricePaise ||
            existing.sellingPricePaise != toSave.sellingPricePaise ||
            existing.wholesalePricePaise != toSave.wholesalePricePaise ||
            existing.mrpPaise != toSave.mrpPaise ||
            existing.defaultDiscountPercentScaled != toSave.defaultDiscountPercentScaled

        if (priceChanged) {
            advancedDao.insertPriceHistory(
                PriceHistory(
                    businessId = bizId,
                    productId = id,
                    productName = toSave.name,
                    purchasePricePaise = toSave.purchasePricePaise,
                    sellingPricePaise = toSave.sellingPricePaise,
                    wholesalePricePaise = toSave.wholesalePricePaise,
                    mrpPaise = toSave.mrpPaise,
                    discountPercentScaled = toSave.defaultDiscountPercentScaled,
                    changeSource = if (isNew) "INITIAL_PRICE" else "PRODUCT_EDIT",
                    changedAtMillis = now
                )
            )
        }

        // Track opening/configured batch if batch or expiry metadata is provided
        if (!toSave.isServiceItem && (toSave.batchNumber.isNotBlank() || toSave.expiryDateMillis != null)) {
            val batchNo = toSave.batchNumber.ifBlank { "BATCH-$id" }
            val existingBatch = advancedDao.findBatchByProductAndNumber(id, batchNo)
            if (existingBatch == null) {
                advancedDao.insertBatch(
                    ProductBatch(
                        businessId = bizId,
                        productId = id,
                        batchNumber = batchNo,
                        expiryDateMillis = toSave.expiryDateMillis,
                        purchaseDateMillis = now,
                        initialQuantityScaled = toSave.currentStockScaled,
                        remainingQuantityScaled = toSave.currentStockScaled,
                        unitCostPaise = toSave.purchasePricePaise,
                        createdAt = now
                    )
                )
            } else if (existingBatch.expiryDateMillis != toSave.expiryDateMillis) {
                advancedDao.updateBatch(existingBatch.copy(expiryDateMillis = toSave.expiryDateMillis))
            }
        }

        if (isNew && !toSave.isServiceItem && toSave.openingStockScaled > 0L) {
            val defaultWarehouseId = inventoryDao.getAllWarehousesOnce(bizId).firstOrNull()?.id ?: 1L
            inventoryDao.insertStockMovement(
                StockMovement(
                    businessId = bizId,
                    productId = id,
                    warehouseId = defaultWarehouseId,
                    movementType = StockMovementType.OPENING,
                    quantityDeltaScaled = toSave.openingStockScaled,
                    resultingStockScaled = toSave.openingStockScaled,
                    unitCostPaise = toSave.purchasePricePaise,
                    referenceType = "OPENING",
                    referenceId = id,
                    batchNumber = toSave.batchNumber,
                    notes = "Opening stock",
                    createdAt = now
                )
            )
            val openingValuePaise = computeStockValuePaise(toSave.openingStockScaled, toSave.purchasePricePaise)
            if (openingValuePaise > 0L) {
                ledgerService.postJournal(
                    description = "Opening stock valuation for ${toSave.name}",
                    referenceType = "OPENING_STOCK",
                    referenceId = id,
                    dateMillis = now,
                    lines = listOf(
                        LedgerPostingLine(
                            accountCode = HisabPulseDatabase.Companion.AccountCodes.INVENTORY,
                            debitPaise = openingValuePaise
                        ),
                        LedgerPostingLine(
                            accountCode = HisabPulseDatabase.Companion.AccountCodes.OWNER_EQUITY,
                            creditPaise = openingValuePaise
                        )
                    ),
                    businessId = bizId
                )
            }
        }

        coreDao.insertAuditLog(
            AuditLog(
                businessId = bizId,
                action = if (isNew) "CREATE_PRODUCT" else "UPDATE_PRODUCT",
                entityType = "Product",
                entityId = id,
                summary = "${if (isNew) "Added" else "Updated"} product ${toSave.name}"
            )
        )
        id
    }.also { savedId ->
        inventoryDao.getProductByIdOnce(savedId)?.let { cloudSyncRepository?.enqueueProductSync(it) }
    }

    suspend fun deleteProductSafely(productId: Long) {
        db.withTransaction {
            val product = inventoryDao.getProductByIdOnce(productId) ?: return@withTransaction
            inventoryDao.softDeleteProduct(productId)
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = product.businessId,
                    action = "ARCHIVE_PRODUCT",
                    entityType = "Product",
                    entityId = productId,
                    summary = "Archived product ${product.name} (preserved historical invoices)"
                )
            )
        }
        inventoryDao.getProductByIdOnce(productId)?.let { cloudSyncRepository?.enqueueProductSync(it) }
    }

    suspend fun adjustStock(
        productId: Long,
        quantityDeltaScaled: Long,
        warehouseId: Long? = null,
        notes: String = "Manual stock adjustment"
    ): Result<Unit> = runCatching {
        db.withTransaction {
            val product = requireNotNull(inventoryDao.getProductByIdOnce(productId)) {
                "Product not found"
            }
            val resolvedWarehouseId = warehouseId ?: inventoryDao.getAllWarehousesOnce(product.businessId).firstOrNull()?.id
            val settings = coreDao.getAppSettingsOnce()
            val allowNegative = settings?.allowNegativeStock ?: false
            val newStock = product.currentStockScaled + quantityDeltaScaled
            require(allowNegative || newStock >= 0L) {
                "Insufficient stock for ${product.name}. Current stock cannot go negative."
            }
            inventoryDao.adjustProductStock(productId, quantityDeltaScaled)
            val type = if (quantityDeltaScaled >= 0L) {
                StockMovementType.ADJUSTMENT_IN
            } else {
                StockMovementType.ADJUSTMENT_OUT
            }
            inventoryDao.insertStockMovement(
                StockMovement(
                    businessId = product.businessId,
                    productId = productId,
                    warehouseId = resolvedWarehouseId,
                    movementType = type,
                    quantityDeltaScaled = quantityDeltaScaled,
                    resultingStockScaled = newStock,
                    unitCostPaise = product.purchasePricePaise,
                    referenceType = "ADJUSTMENT",
                    notes = notes
                )
            )
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = product.businessId,
                    action = "STOCK_ADJUSTMENT",
                    entityType = "Product",
                    entityId = productId,
                    summary = "Stock adjusted by ${quantityDeltaScaled / 100.0} for ${product.name}: $notes"
                )
            )
        }
        inventoryDao.getProductByIdOnce(productId)?.let { cloudSyncRepository?.enqueueProductSync(it) }
    }

    suspend fun transferStock(
        productId: Long,
        fromWarehouseId: Long,
        toWarehouseId: Long,
        quantityScaled: Long,
        notes: String = ""
    ): Result<Unit> = runCatching {
        require(fromWarehouseId != toWarehouseId) { "Select different source and destination warehouses" }
        require(quantityScaled > 0L) { "Transfer quantity must be positive" }
        db.withTransaction {
            val product = requireNotNull(inventoryDao.getProductByIdOnce(productId)) { "Product not found" }
            val now = System.currentTimeMillis()
            inventoryDao.insertStockMovement(
                StockMovement(
                    businessId = product.businessId,
                    productId = productId,
                    warehouseId = fromWarehouseId,
                    movementType = StockMovementType.TRANSFER_OUT,
                    quantityDeltaScaled = -quantityScaled,
                    resultingStockScaled = product.currentStockScaled,
                    referenceType = "TRANSFER",
                    notes = "Transfer out: $notes",
                    createdAt = now
                )
            )
            inventoryDao.insertStockMovement(
                StockMovement(
                    businessId = product.businessId,
                    productId = productId,
                    warehouseId = toWarehouseId,
                    movementType = StockMovementType.TRANSFER_IN,
                    quantityDeltaScaled = quantityScaled,
                    resultingStockScaled = product.currentStockScaled,
                    referenceType = "TRANSFER",
                    notes = "Transfer in: $notes",
                    createdAt = now
                )
            )
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = product.businessId,
                    action = "STOCK_TRANSFER",
                    entityType = "Product",
                    entityId = productId,
                    summary = "Transferred ${quantityScaled / 100.0} ${product.unitName} of ${product.name}"
                )
            )
        }
    }

    suspend fun recordManufacturingConversion(
        rawMaterialId: Long,
        rawQtyScaled: Long,
        finishedProductId: Long,
        finishedQtyScaled: Long,
        notes: String = "Production batch"
    ): Result<Unit> = runCatching {
        require(rawMaterialId != finishedProductId) { "Raw material and finished product must differ" }
        require(rawQtyScaled > 0L && finishedQtyScaled > 0L) { "Quantities must be positive" }
        db.withTransaction {
            val raw = requireNotNull(inventoryDao.getProductByIdOnce(rawMaterialId))
            val finished = requireNotNull(inventoryDao.getProductByIdOnce(finishedProductId))
            val allowNegative = coreDao.getAppSettingsOnce()?.allowNegativeStock ?: false
            val newRawStock = raw.currentStockScaled - rawQtyScaled
            require(allowNegative || newRawStock >= 0L) { "Insufficient raw material stock for ${raw.name}" }

            val newFinStock = finished.currentStockScaled + finishedQtyScaled
            inventoryDao.adjustProductStock(rawMaterialId, -rawQtyScaled)
            inventoryDao.adjustProductStock(finishedProductId, finishedQtyScaled)

            val now = System.currentTimeMillis()
            val defaultWh = inventoryDao.getAllWarehousesOnce(raw.businessId).firstOrNull()?.id
            inventoryDao.insertStockMovement(
                StockMovement(
                    businessId = raw.businessId,
                    productId = rawMaterialId,
                    warehouseId = defaultWh,
                    movementType = StockMovementType.PRODUCTION_CONSUME,
                    quantityDeltaScaled = -rawQtyScaled,
                    resultingStockScaled = newRawStock,
                    unitCostPaise = raw.purchasePricePaise,
                    referenceType = "PRODUCTION",
                    notes = notes,
                    createdAt = now
                )
            )
            inventoryDao.insertStockMovement(
                StockMovement(
                    businessId = finished.businessId,
                    productId = finishedProductId,
                    warehouseId = defaultWh,
                    movementType = StockMovementType.PRODUCTION_OUTPUT,
                    quantityDeltaScaled = finishedQtyScaled,
                    resultingStockScaled = newFinStock,
                    unitCostPaise = finished.purchasePricePaise,
                    referenceType = "PRODUCTION",
                    notes = notes,
                    createdAt = now
                )
            )
        }
        inventoryDao.getProductByIdOnce(rawMaterialId)?.let { cloudSyncRepository?.enqueueProductSync(it) }
        inventoryDao.getProductByIdOnce(finishedProductId)?.let { cloudSyncRepository?.enqueueProductSync(it) }
    }

    suspend fun bulkUpdateSellingPrices(productIds: List<Long>, percentChange: BigDecimal) = db.withTransaction {
        val factor = BigDecimal.ONE + percentChange.divide(BigDecimal("100"), 6, RoundingMode.HALF_UP)
        val now = System.currentTimeMillis()
        var bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        for (id in productIds) {
            val product = inventoryDao.getProductByIdOnce(id) ?: continue
            bizId = product.businessId
            val newPrice = BigDecimal(product.sellingPricePaise)
                .multiply(factor)
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
                .coerceAtLeast(0L)
            inventoryDao.updateProduct(product.copy(sellingPricePaise = newPrice, updatedAt = now))
            advancedDao.insertPriceHistory(
                PriceHistory(
                    businessId = product.businessId,
                    productId = product.id,
                    productName = product.name,
                    purchasePricePaise = product.purchasePricePaise,
                    sellingPricePaise = newPrice,
                    wholesalePricePaise = product.wholesalePricePaise,
                    mrpPaise = product.mrpPaise,
                    discountPercentScaled = product.defaultDiscountPercentScaled,
                    changeSource = "BULK_UPDATE",
                    changedAtMillis = now
                )
            )
        }
        coreDao.insertAuditLog(
            AuditLog(
                businessId = bizId,
                action = "BULK_PRICE_UPDATE",
                entityType = "Product",
                entityId = 0L,
                summary = "Updated selling prices by $percentChange% for ${productIds.size} products"
            )
        )
        for (id in productIds) {
            inventoryDao.getProductByIdOnce(id)?.let { cloudSyncRepository?.enqueueProductSync(it) }
        }
    }

    companion object {
        fun computeStockValuePaise(qtyScaled: Long, unitPricePaise: Long): Long {
            return BigDecimal(qtyScaled)
                .multiply(BigDecimal(unitPricePaise))
                .divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
                .toLong()
        }
    }
}
