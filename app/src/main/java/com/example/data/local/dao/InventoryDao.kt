package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.Product
import com.example.data.local.entities.ProductCategory
import com.example.data.local.entities.StockMovement
import com.example.data.local.entities.UnitEntity
import com.example.data.local.entities.Warehouse
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM products WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
    fun observeActiveProducts(businessId: Long = 1L): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getAllProductsOnce(businessId: Long = 1L): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeProductById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductByIdOnce(id: Long): Product?

    @Query("SELECT * FROM products WHERE businessId = :businessId AND barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun findProductByBarcode(barcode: String, businessId: Long = 1L): Product?

    @Query("SELECT * FROM products WHERE businessId = :businessId AND LOWER(name) = LOWER(:name) AND isActive = 1 LIMIT 1")
    suspend fun findProductByName(name: String, businessId: Long = 1L): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET isActive = 0, updatedAt = :now WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET currentStockScaled = currentStockScaled + :deltaScaled, updatedAt = :now WHERE id = :productId")
    suspend fun adjustProductStock(productId: Long, deltaScaled: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products WHERE businessId = :businessId AND isActive = 1 AND isServiceItem = 0 AND currentStockScaled <= 0 ORDER BY name ASC")
    fun observeOutOfStockProducts(businessId: Long = 1L): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE businessId = :businessId AND isActive = 1 AND isServiceItem = 0 AND currentStockScaled > 0 AND currentStockScaled <= minStockScaled ORDER BY currentStockScaled ASC")
    fun observeLowStockProducts(businessId: Long = 1L): Flow<List<Product>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC")
    fun observeMovementsForProduct(productId: Long): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentMovements(businessId: Long = 1L, limit: Int = 200): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId ORDER BY createdAt DESC")
    suspend fun getAllMovementsOnce(businessId: Long = 1L): List<StockMovement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovement): Long

    @Query("SELECT * FROM product_categories WHERE businessId = :businessId ORDER BY name ASC")
    fun observeCategories(businessId: Long = 1L): Flow<List<ProductCategory>>

    @Query("SELECT * FROM product_categories WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getAllCategoriesOnce(businessId: Long = 1L): List<ProductCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ProductCategory): Long

    @Delete
    suspend fun deleteCategory(category: ProductCategory)

    @Query("SELECT * FROM units WHERE businessId = :businessId ORDER BY name ASC")
    fun observeUnits(businessId: Long = 1L): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getAllUnitsOnce(businessId: Long = 1L): List<UnitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: UnitEntity): Long

    @Delete
    suspend fun deleteUnit(unit: UnitEntity)

    @Query("SELECT * FROM warehouses WHERE businessId = :businessId ORDER BY isDefault DESC, name ASC")
    fun observeWarehouses(businessId: Long = 1L): Flow<List<Warehouse>>

    @Query("SELECT * FROM warehouses WHERE businessId = :businessId ORDER BY isDefault DESC, name ASC")
    suspend fun getAllWarehousesOnce(businessId: Long = 1L): List<Warehouse>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehouse(warehouse: Warehouse): Long
}
