package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.Customer
import com.example.data.local.entities.Supplier
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM customers WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
    fun observeCustomers(businessId: Long = 1L): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getAllCustomersOnce(businessId: Long = 1L): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeCustomerById(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerByIdOnce(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE businessId = :businessId AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findCustomerByName(name: String, businessId: Long = 1L): Customer?

    @Query("SELECT * FROM customers WHERE businessId = :businessId AND phone = :phone AND phone != '' LIMIT 1")
    suspend fun findCustomerByPhone(phone: String, businessId: Long = 1L): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("UPDATE customers SET currentBalancePaise = currentBalancePaise + :deltaPaise, updatedAt = :now WHERE id = :customerId")
    suspend fun adjustCustomerBalance(customerId: Long, deltaPaise: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET advanceBalancePaise = advanceBalancePaise + :deltaPaise, updatedAt = :now WHERE id = :customerId")
    suspend fun adjustCustomerAdvance(customerId: Long, deltaPaise: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(SUM(CASE WHEN currentBalancePaise > 0 THEN currentBalancePaise ELSE 0 END), 0) FROM customers WHERE businessId = :businessId AND isActive = 1")
    fun observeTotalReceivables(businessId: Long = 1L): Flow<Long>

    @Query("SELECT * FROM suppliers WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
    fun observeSuppliers(businessId: Long = 1L): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getAllSuppliersOnce(businessId: Long = 1L): List<Supplier>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    fun observeSupplierById(id: Long): Flow<Supplier?>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierByIdOnce(id: Long): Supplier?

    @Query("SELECT * FROM suppliers WHERE businessId = :businessId AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findSupplierByName(name: String, businessId: Long = 1L): Supplier?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Query("UPDATE suppliers SET currentBalancePaise = currentBalancePaise + :deltaPaise, updatedAt = :now WHERE id = :supplierId")
    suspend fun adjustSupplierBalance(supplierId: Long, deltaPaise: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE suppliers SET advanceBalancePaise = advanceBalancePaise + :deltaPaise, updatedAt = :now WHERE id = :supplierId")
    suspend fun adjustSupplierAdvance(supplierId: Long, deltaPaise: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(SUM(CASE WHEN currentBalancePaise > 0 THEN currentBalancePaise ELSE 0 END), 0) FROM suppliers WHERE businessId = :businessId AND isActive = 1")
    fun observeTotalPayables(businessId: Long = 1L): Flow<Long>
}
