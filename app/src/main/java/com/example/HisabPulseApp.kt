package com.example

import android.app.Application
import com.example.data.local.HisabPulseDatabase
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.AccountingRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.EngagementAndOpsRepository
import com.example.data.repository.IntelligenceRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PartyRepository
import com.example.data.repository.RecoveryAndImportRepository
import com.example.data.repository.SearchHistoryRepository
import com.example.data.worker.EncryptedDailyBackupWorker
import com.example.data.worker.IntegrityCheckWorker
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Application) {
    val database: HisabPulseDatabase = HisabPulseDatabase.getInstance(context)
    val preferences: AppPreferencesRepository = AppPreferencesRepository(context)
    val cloudSyncRepository: CloudSyncRepository = CloudSyncRepository(context, database)
    val businessRepository: BusinessRepository = BusinessRepository(database, preferences, cloudSyncRepository)
    val partyRepository: PartyRepository = PartyRepository(database)
    val inventoryRepository: InventoryRepository = InventoryRepository(database, cloudSyncRepository)
    val billingRepository: BillingRepository = BillingRepository(database, cloudSyncRepository)
    val accountingRepository: AccountingRepository = AccountingRepository(database, cloudSyncRepository)
    val backupRepository: BackupRepository = BackupRepository(context, database, preferences)
    val intelligenceRepository: IntelligenceRepository = IntelligenceRepository(database)
    val engagementRepository: EngagementAndOpsRepository = EngagementAndOpsRepository(
        db = database,
        billingRepository = billingRepository,
        accountingRepository = accountingRepository
    )
    val recoveryRepository: RecoveryAndImportRepository = RecoveryAndImportRepository(
        context = context,
        db = database,
        backupRepository = backupRepository,
        businessRepository = businessRepository
    )
    val searchHistoryRepository: SearchHistoryRepository = SearchHistoryRepository(database)
}

class HisabPulseApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            HisabPulseDatabase.ensureSystemSeeded(container.database)
            if (FirebaseApp.getApps(this@HisabPulseApp).isNotEmpty()) {
                val signedInUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                if (!signedInUid.isNullOrBlank()) {
                    container.cloudSyncRepository.startSyncForUser(signedInUid)
                }
            }
        }
        IntegrityCheckWorker.schedule(this)
        EncryptedDailyBackupWorker.schedule(this)
    }
}
