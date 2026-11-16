package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.SearchHistory
import com.example.data.local.entities.SearchModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryRepository(private val db: HisabPulseDatabase) {
    private val coreDao = db.coreDao()
    private val advancedDao = db.advancedDao()

    private val activeBizFlow: Flow<Long> = coreDao.observeAppSettings()
        .map { it?.activeBusinessId ?: 1L }
        .distinctUntilChanged()

    fun observeRecentSearches(module: SearchModule, limit: Int = 10): Flow<List<SearchHistory>> =
        activeBizFlow.flatMapLatest { bizId ->
            advancedDao.observeRecentSearches(bizId, module, limit)
        }

    suspend fun getRecentSearchesOnce(module: SearchModule, limit: Int = 10): List<SearchHistory> =
        withContext(Dispatchers.IO) {
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            advancedDao.getRecentSearchesOnce(bizId, module, limit)
        }

    suspend fun recordSearch(module: SearchModule, rawQuery: String, maxHistory: Int = 10) =
        withContext(Dispatchers.IO) {
            val cleaned = rawQuery.trim()
            if (cleaned.length < 2) return@withContext
            val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
            db.withTransaction {
                advancedDao.deleteSearchByQueryIgnoreCase(bizId, module, cleaned)
                advancedDao.upsertSearchHistory(
                    SearchHistory(
                        businessId = bizId,
                        module = module,
                        query = cleaned,
                        searchedAtMillis = System.currentTimeMillis()
                    )
                )
                advancedDao.trimSearchHistory(bizId, module, maxHistory)
            }
        }

    suspend fun deleteSearchEntry(id: Long) = withContext(Dispatchers.IO) {
        advancedDao.deleteSearchHistoryItem(id)
    }

    suspend fun clearHistory(module: SearchModule) = withContext(Dispatchers.IO) {
        val bizId = coreDao.getAppSettingsOnce()?.activeBusinessId ?: 1L
        advancedDao.clearSearchHistoryForModule(bizId, module)
    }
}
