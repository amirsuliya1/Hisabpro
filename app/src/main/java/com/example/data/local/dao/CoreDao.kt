package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.AppSettings
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.BackupMetadata
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.InvoiceTemplate
import com.example.data.local.entities.TaxConfiguration
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreDao {
    @Query("SELECT * FROM business_profiles WHERE id = COALESCE((SELECT activeBusinessId FROM app_settings WHERE id = 1), 1) LIMIT 1")
    fun observeBusinessProfile(): Flow<BusinessProfile?>

    @Query("SELECT * FROM business_profiles WHERE id = COALESCE((SELECT activeBusinessId FROM app_settings WHERE id = 1), 1) LIMIT 1")
    suspend fun getBusinessProfileOnce(): BusinessProfile?

    @Query("SELECT * FROM business_profiles ORDER BY id ASC")
    fun observeAllBusinessProfiles(): Flow<List<BusinessProfile>>

    @Query("SELECT * FROM business_profiles ORDER BY id ASC")
    suspend fun getAllBusinessProfilesOnce(): List<BusinessProfile>

    @Query("SELECT * FROM business_profiles WHERE id = :id LIMIT 1")
    suspend fun getBusinessProfileByIdOnce(id: Long): BusinessProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusinessProfile(profile: BusinessProfile): Long

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeAppSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getAppSettingsOnce(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(settings: AppSettings)

    @Query("SELECT * FROM tax_configurations WHERE businessId = :businessId ORDER BY ratePercentScaled ASC")
    fun observeTaxConfigurations(businessId: Long = 1L): Flow<List<TaxConfiguration>>

    @Query("SELECT * FROM tax_configurations WHERE businessId = :businessId ORDER BY ratePercentScaled ASC")
    suspend fun getAllTaxConfigurationsOnce(businessId: Long = 1L): List<TaxConfiguration>

    @Query("SELECT * FROM tax_configurations ORDER BY id ASC")
    suspend fun getEveryTaxConfigurationOnce(): List<TaxConfiguration>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxConfiguration(config: TaxConfiguration): Long

    @Delete
    suspend fun deleteTaxConfiguration(config: TaxConfiguration)

    @Query("SELECT * FROM invoice_templates WHERE businessId = :businessId ORDER BY id ASC")
    fun observeInvoiceTemplates(businessId: Long = 1L): Flow<List<InvoiceTemplate>>

    @Query("SELECT * FROM invoice_templates WHERE businessId = :businessId ORDER BY id ASC")
    suspend fun getAllInvoiceTemplatesOnce(businessId: Long = 1L): List<InvoiceTemplate>

    @Query("SELECT * FROM invoice_templates ORDER BY id ASC")
    suspend fun getEveryInvoiceTemplateOnce(): List<InvoiceTemplate>

    @Query("SELECT * FROM invoice_templates WHERE businessId = :businessId AND isSelected = 1 LIMIT 1")
    fun observeSelectedTemplate(businessId: Long = 1L): Flow<InvoiceTemplate?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceTemplate(template: InvoiceTemplate): Long

    @Update
    suspend fun updateInvoiceTemplate(template: InvoiceTemplate)

    @Delete
    suspend fun deleteInvoiceTemplate(template: InvoiceTemplate)

    @Query("UPDATE invoice_templates SET isSelected = (id = :templateId) WHERE businessId = :businessId")
    suspend fun selectTemplate(templateId: Long, businessId: Long = 1L)

    @Query("SELECT * FROM backup_metadata WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun observeBackupMetadata(businessId: Long = 1L): Flow<List<BackupMetadata>>

    @Query("SELECT * FROM backup_metadata WHERE businessId = :businessId ORDER BY createdAt DESC")
    suspend fun getAllBackupMetadataOnce(businessId: Long = 1L): List<BackupMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(metadata: BackupMetadata): Long

    @Query("SELECT * FROM audit_logs WHERE businessId = :businessId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentAuditLogs(businessId: Long = 1L, limit: Int = 200): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE businessId = :businessId ORDER BY createdAt DESC")
    suspend fun getAllAuditLogsOnce(businessId: Long = 1L): List<AuditLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog): Long
}
