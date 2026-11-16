package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "product_categories",
    indices = [Index(value = ["businessId", "name"], unique = true)]
)
data class ProductCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "units",
    indices = [Index(value = ["businessId", "shortName"], unique = true)]
)
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val shortName: String,
    val baseUnitShortName: String = "",
    val conversionFactorScaled: Long = 100 // 100 = 1.00
)

@Serializable
@Entity(
    tableName = "warehouses",
    indices = [Index(value = ["businessId", "name"], unique = true)]
)
data class Warehouse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val location: String = "",
    val isDefault: Boolean = false
)

@Serializable
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ProductCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("businessId"),
        Index("categoryId"),
        Index("unitId"),
        Index("barcode"),
        Index("sku"),
        Index("name")
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val imageUri: String = "",
    val sku: String = "",
    val barcode: String = "",
    val productCode: String = "",
    val hsnSacCode: String = "",
    val categoryId: Long? = null,
    val categoryName: String = "General",
    val brand: String = "",
    val description: String = "",
    val purchasePricePaise: Long = 0L,
    val sellingPricePaise: Long = 0L,
    val wholesalePricePaise: Long = 0L,
    val wholesaleMinQtyScaled: Long = 1000L, // 10.00 units
    val mrpPaise: Long = 0L,
    val taxRatePercentScaled: Long = 0L, // 1800 = 18.00%
    val isTaxInclusive: Boolean = false,
    val unitId: Long? = null,
    val unitName: String = "PCS",
    val openingStockScaled: Long = 0L, // 100 = 1.00 qty
    val currentStockScaled: Long = 0L,
    val minStockScaled: Long = 500L, // 5.00 units
    // Industry-specific optional metadata
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,
    val garmentSize: String = "",
    val garmentColor: String = "",
    val garmentStyle: String = "",
    val serialNumber: String = "",
    val warrantyMonths: Int = 0,
    val isServiceItem: Boolean = false,
    val isRawMaterial: Boolean = false,
    val defaultDiscountPercentScaled: Long = 0L,
    val trackExpiry: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Warehouse::class,
            parentColumns = ["id"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("businessId"), Index("productId"), Index("warehouseId"), Index("createdAt")]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val productId: Long,
    val warehouseId: Long? = 1L,
    val movementType: StockMovementType,
    val quantityDeltaScaled: Long, // positive for IN, negative for OUT
    val resultingStockScaled: Long,
    val unitCostPaise: Long = 0L,
    val referenceType: String = "", // INVOICE, PURCHASE, ADJUSTMENT, TRANSFER, PRODUCTION
    val referenceId: Long? = null,
    val batchNumber: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
