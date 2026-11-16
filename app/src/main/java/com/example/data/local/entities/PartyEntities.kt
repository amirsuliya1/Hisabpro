package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "customers",
    indices = [Index("businessId"), Index("name"), Index("phone")]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val state: String = "",
    val gstin: String = "",
    val category: String = "Regular",
    val openingBalancePaise: Long = 0L, // Positive = receivable from customer
    val currentBalancePaise: Long = 0L, // Net receivable (positive = owes us, negative = advance)
    val advanceBalancePaise: Long = 0L,
    val creditLimitPaise: Long = 0L,
    val defaultDueDays: Int = 15,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "suppliers",
    indices = [Index("businessId"), Index("name"), Index("phone")]
)
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val state: String = "",
    val gstin: String = "",
    val openingBalancePaise: Long = 0L, // Positive = payable to supplier
    val currentBalancePaise: Long = 0L, // Net payable (positive = we owe supplier)
    val advanceBalancePaise: Long = 0L,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
