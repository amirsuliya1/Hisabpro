package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AppSettings
import com.example.data.local.entities.AppThemeMode
import com.example.data.local.entities.AuditLog
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.InvoiceTemplate
import com.example.data.local.entities.ProductCategory
import com.example.data.local.entities.TaxConfiguration
import com.example.data.local.entities.UnitEntity
import com.example.data.local.entities.Warehouse
import com.example.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessRepository(
    private val db: HisabPulseDatabase,
    private val prefs: AppPreferencesRepository,
    private val cloudSyncRepository: CloudSyncRepository? = null
) {
    private val coreDao = db.coreDao()
    private val inventoryDao = db.inventoryDao()

    val appSettings: Flow<AppSettings?> = coreDao.observeAppSettings()
    val activeBusinessIdFlow: Flow<Long> = appSettings.map { it?.activeBusinessId ?: 1L }.distinctUntilChanged()

    val businessProfile: Flow<BusinessProfile?> = coreDao.observeBusinessProfile()
    val allBusinessProfiles: Flow<List<BusinessProfile>> = coreDao.observeAllBusinessProfiles()

    val taxConfigurations: Flow<List<TaxConfiguration>> = activeBusinessIdFlow.flatMapLatest { bizId ->
        coreDao.observeTaxConfigurations(bizId)
    }
    val invoiceTemplates: Flow<List<InvoiceTemplate>> = activeBusinessIdFlow.flatMapLatest { bizId ->
        coreDao.observeInvoiceTemplates(bizId)
    }
    val selectedTemplate: Flow<InvoiceTemplate?> = activeBusinessIdFlow.flatMapLatest { bizId ->
        coreDao.observeSelectedTemplate(bizId)
    }
    val categories: Flow<List<ProductCategory>> = activeBusinessIdFlow.flatMapLatest { bizId ->
        inventoryDao.observeCategories(bizId)
    }
    val units: Flow<List<UnitEntity>> = activeBusinessIdFlow.flatMapLatest { bizId ->
        inventoryDao.observeUnits(bizId)
    }
    val warehouses: Flow<List<Warehouse>> = activeBusinessIdFlow.flatMapLatest { bizId ->
        inventoryDao.observeWarehouses(bizId)
    }
    val auditLogs: Flow<List<AuditLog>> = activeBusinessIdFlow.flatMapLatest { bizId ->
        coreDao.observeRecentAuditLogs(bizId)
    }

    suspend fun getActiveBusinessId(): Long = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L

    suspend fun getBusinessProfileOnce(): BusinessProfile? = coreDao.getBusinessProfileOnce()

    suspend fun saveBusinessProfile(profile: BusinessProfile, completeOnboarding: Boolean = false) {
        val activeId = getActiveBusinessId()
        val targetId = if (completeOnboarding && coreDao.getBusinessProfileByIdOnce(activeId) == null) {
            activeId
        } else if (profile.id > 0L) {
            profile.id
        } else {
            activeId
        }
        db.withTransaction {
            val saved = profile.copy(id = targetId, updatedAt = System.currentTimeMillis())
            coreDao.upsertBusinessProfile(saved)
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = targetId,
                    action = "SAVE_PROFILE",
                    entityType = "BusinessProfile",
                    entityId = targetId,
                    summary = "Updated business profile: ${saved.businessName}"
                )
            )
            if (completeOnboarding) {
                val existing = coreDao.getAppSettingsOnce() ?: AppSettings(id = 1L, activeBusinessId = targetId)
                coreDao.upsertAppSettings(
                    existing.copy(
                        activeBusinessId = targetId,
                        onboardingCompleted = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        HisabPulseDatabase.ensureBusinessSeeded(db, targetId)
        if (completeOnboarding) {
            prefs.setOnboardingCompleted(true)
        }
        coreDao.getBusinessProfileByIdOnce(targetId)?.let { cloudSyncRepository?.enqueueBusinessSync(it) }
    }

    suspend fun createBusinessProfile(
        profile: BusinessProfile,
        switchToNewBusiness: Boolean = true
    ): Long {
        val newId = db.withTransaction {
            val existingMax = coreDao.getAllBusinessProfilesOnce().maxOfOrNull { it.id } ?: 0L
            val assignedId = if (existingMax < 1L) 1L else existingMax + 1L
            val toSave = profile.copy(id = assignedId, updatedAt = System.currentTimeMillis())
            coreDao.upsertBusinessProfile(toSave)
            if (switchToNewBusiness) {
                val settings = coreDao.getAppSettingsOnce() ?: AppSettings(id = 1L)
                coreDao.upsertAppSettings(
                    settings.copy(
                        activeBusinessId = assignedId,
                        onboardingCompleted = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            coreDao.insertAuditLog(
                AuditLog(
                    businessId = assignedId,
                    action = "CREATE_BUSINESS",
                    entityType = "BusinessProfile",
                    entityId = assignedId,
                    summary = "Created business profile: ${toSave.businessName}"
                )
            )
            assignedId
        }
        HisabPulseDatabase.ensureBusinessSeeded(db, newId)
        if (switchToNewBusiness) {
            prefs.setOnboardingCompleted(true)
        }
        coreDao.getBusinessProfileByIdOnce(newId)?.let { cloudSyncRepository?.enqueueBusinessSync(it) }
        return newId
    }

    suspend fun switchActiveBusiness(businessId: Long): Result<Unit> = runCatching {
        val profile = requireNotNull(coreDao.getBusinessProfileByIdOnce(businessId)) {
            "Business profile not found"
        }
        HisabPulseDatabase.ensureBusinessSeeded(db, businessId)
        updateAppSettings { it.copy(activeBusinessId = businessId) }
        coreDao.insertAuditLog(
            AuditLog(
                businessId = businessId,
                action = "SWITCH_BUSINESS",
                entityType = "BusinessProfile",
                entityId = businessId,
                summary = "Switched active business to ${profile.businessName}"
            )
        )
    }

    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        val current = coreDao.getAppSettingsOnce() ?: AppSettings(id = 1L)
        val updated = transform(current).copy(id = 1L, updatedAt = System.currentTimeMillis())
        coreDao.upsertAppSettings(updated)
        prefs.setThemeMode(updated.themeMode)
        prefs.setHideAmounts(updated.hideAmounts)
        prefs.setLanguageCode(updated.languageCode)
    }

    suspend fun setTheme(mode: AppThemeMode) {
        updateAppSettings { it.copy(themeMode = mode) }
    }

    suspend fun saveTaxConfiguration(config: TaxConfiguration) {
        val bizId = getActiveBusinessId()
        coreDao.insertTaxConfiguration(config.copy(businessId = bizId))
    }

    suspend fun deleteTaxConfiguration(config: TaxConfiguration) {
        coreDao.deleteTaxConfiguration(config)
    }

    suspend fun selectInvoiceTemplate(templateId: Long) {
        val bizId = getActiveBusinessId()
        coreDao.selectTemplate(templateId, bizId)
    }

    suspend fun updateInvoiceTemplate(template: InvoiceTemplate) {
        coreDao.updateInvoiceTemplate(template)
    }

    suspend fun createCustomInvoiceTemplate(template: InvoiceTemplate): Long {
        val bizId = getActiveBusinessId()
        val insertedId = coreDao.insertInvoiceTemplate(
            template.copy(
                id = 0L,
                businessId = bizId,
                isSelected = true
            )
        )
        coreDao.selectTemplate(insertedId, bizId)
        return insertedId
    }

    suspend fun deleteCustomInvoiceTemplate(template: InvoiceTemplate) {
        val bizId = getActiveBusinessId()
        val all = coreDao.getAllInvoiceTemplatesOnce(bizId)
        if (all.size <= 1) return
        coreDao.deleteInvoiceTemplate(template)
        val remaining = coreDao.getAllInvoiceTemplatesOnce(bizId)
        if (remaining.isNotEmpty() && remaining.none { it.isSelected }) {
            coreDao.selectTemplate(remaining.first().id, bizId)
        }
    }

    suspend fun saveCategory(name: String, description: String = "") {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            val bizId = getActiveBusinessId()
            inventoryDao.insertCategory(ProductCategory(businessId = bizId, name = trimmed, description = description))
        }
    }

    suspend fun deleteCategory(category: ProductCategory) {
        inventoryDao.deleteCategory(category)
    }

    suspend fun saveUnit(unit: UnitEntity) {
        val bizId = getActiveBusinessId()
        inventoryDao.insertUnit(unit.copy(businessId = bizId))
    }

    suspend fun deleteUnit(unit: UnitEntity) {
        inventoryDao.deleteUnit(unit)
    }

    suspend fun saveWarehouse(warehouse: Warehouse) {
        val bizId = getActiveBusinessId()
        inventoryDao.insertWarehouse(warehouse.copy(businessId = bizId))
    }
}
