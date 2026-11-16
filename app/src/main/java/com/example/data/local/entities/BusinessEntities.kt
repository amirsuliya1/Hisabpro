package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "business_profiles")
data class BusinessProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 1,
    val businessName: String,
    val ownerName: String = "",
    val logoUri: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pinCode: String = "",
    val category: BusinessCategory = BusinessCategory.RETAIL,
    val isGstRegistered: Boolean = true,
    val gstin: String = "",
    val financialYear: String = "FY 2026-27",
    val currencySymbol: String = "₹",
    val invoicePrefix: String = "INV-",
    val nextInvoiceNumber: Long = 1001,
    val upiId: String = "",
    val signatureLabel: String = "Authorized Signatory",
    val defaultTerms: String = "Thank you for your business!",
    val tagline: String = "",
    val website: String = "",
    val signatureUri: String = "",
    val sealUri: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "tax_configurations",
    indices = [Index("businessId")]
)
data class TaxConfiguration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val ratePercentScaled: Long, // 1800 = 18.00%
    val cgstPercentScaled: Long,
    val sgstPercentScaled: Long,
    val igstPercentScaled: Long,
    val hsnSacDefault: String = "",
    val isDefault: Boolean = false,
    val isActive: Boolean = true
)

@Serializable
@Entity(
    tableName = "invoice_templates",
    indices = [Index("businessId")]
)
data class InvoiceTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val code: String, // CORPORATE_WAVE, MODERN_ORANGE, CLASSIC_GST, COMPACT_POS, MODERN_RETAIL, WHOLESALE_PRO, MINIMAL_CLEAN, DETAILED_GST, THERMAL_58
    val name: String,
    val description: String,
    val showLogo: Boolean = true,
    val logoPosition: String = "LEFT", // LEFT, CENTER, RIGHT
    val showBusinessName: Boolean = true,
    val showAddress: Boolean = true,
    val showGstin: Boolean = true,
    val showHsn: Boolean = true,
    val showTaxBreakdown: Boolean = true,
    val showDiscountColumn: Boolean = true,
    val showPaymentInfo: Boolean = true,
    val showSignature: Boolean = true,
    val showUpiQr: Boolean = true,
    val showCustomerNotes: Boolean = true,
    val paperSize: InvoicePaperSize = InvoicePaperSize.A4,
    val accentColorHex: String = "#0F2D5C",
    val secondaryColorHex: String = "#0284C7",
    val headerText: String = "",
    val footerMessage: String = "Thank You For Your Business!",
    val termsText: String = "",
    val isSelected: Boolean = false
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Long = 1,
    val activeBusinessId: Long = 1L,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val languageCode: String = "en",
    val allowNegativeStock: Boolean = false,
    val enableRoundOff: Boolean = true,
    val defaultTaxInclusive: Boolean = false,
    val warnExpiredBatchSale: Boolean = true,
    val blockExpiredBatchSale: Boolean = false,
    val posQuickPayMode: PaymentMode = PaymentMode.CASH,
    val enableWholesaleModule: Boolean = false,
    val enableMedicalModule: Boolean = false,
    val enableGarmentsModule: Boolean = false,
    val enableElectronicsModule: Boolean = false,
    val enableRestaurantModule: Boolean = false,
    val enableManufacturingModule: Boolean = false,
    val enableServiceModule: Boolean = false,
    val pinLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val hideAmounts: Boolean = false,
    val autoBackupSchedule: String = "OFF", // OFF, DAILY, WEEKLY
    val autoBackupFolderUri: String = "",
    val lastAutoBackupMillis: Long = 0L,
    val onboardingCompleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "backup_metadata",
    indices = [Index("businessId")]
)
data class BackupMetadata(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val fileName: String,
    val safUriString: String = "",
    val schemaVersion: Int,
    val isEncrypted: Boolean,
    val checksumSha256: String,
    val recordCount: Int,
    val sizeBytes: Long,
    val verificationStatus: String = "VERIFIED",
    val restoreStatus: String = "NONE",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "audit_logs",
    indices = [Index("businessId"), Index("entityType"), Index("createdAt")]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 1L,
    val action: String,
    val entityType: String,
    val entityId: Long,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)
