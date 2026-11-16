package com.example.data.worker

import android.content.Context
import android.net.Uri
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.DateUtils
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AuditLog
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.BackupRepository
import java.util.concurrent.TimeUnit

class IntegrityCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = HisabPulseDatabase.getInstance(applicationContext)
            val bizId = db.coreDao().getAppSettingsOnce()?.activeBusinessId ?: 1L
            val entries = db.accountingDao().getAllLedgerEntriesOnce(bizId)
            val totalDebit = entries.sumOf { it.debitPaise }
            val totalCredit = entries.sumOf { it.creditPaise }
            if (totalDebit == totalCredit) {
                db.coreDao().insertAuditLog(
                    AuditLog(
                        businessId = bizId,
                        action = "INTEGRITY_CHECK",
                        entityType = "Ledger",
                        entityId = 0L,
                        summary = "Automated double-entry balance verified (Debit = Credit = $totalDebit paise)"
                    )
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "hisabpulse_daily_integrity_check"

        fun schedule(context: Context) {
            runCatching {
                val appCtx = context.applicationContext
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(appCtx, Configuration.Builder().build())
                }
                val request = PeriodicWorkRequestBuilder<IntegrityCheckWorker>(24, TimeUnit.HOURS)
                    .build()
                WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}

class BackupScheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = HisabPulseDatabase.getInstance(applicationContext)
            val settings = db.coreDao().getAppSettingsOnce() ?: return Result.success()
            if (settings.autoBackupSchedule == "OFF" || settings.autoBackupFolderUri.isBlank()) {
                return Result.success()
            }
            val now = System.currentTimeMillis()
            val intervalMs = if (settings.autoBackupSchedule == "WEEKLY") {
                7 * 86_400_000L
            } else {
                86_400_000L
            }
            if (now - settings.lastAutoBackupMillis < intervalMs) {
                return Result.success()
            }
            val repo = BackupRepository(applicationContext, db, AppPreferencesRepository(applicationContext))
            val dateStr = DateUtils.formatDate(now).replace(" ", "_")
            val fileName = "HisabPulse_Auto_${settings.autoBackupSchedule}_$dateStr.bizmgr"
            val result = repo.exportAutoBackupToTreeUri(
                treeUri = Uri.parse(settings.autoBackupFolderUri),
                fileName = fileName,
                triggerType = "${settings.autoBackupSchedule}_AUTO"
            )
            if (result.isSuccess) {
                db.coreDao().upsertAppSettings(settings.copy(lastAutoBackupMillis = now, updatedAt = now))
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "hisabpulse_scheduled_saf_backup"

        fun scheduleOrCancel(context: Context, scheduleMode: String) {
            runCatching {
                val appCtx = context.applicationContext
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(appCtx, Configuration.Builder().build())
                }
                val wm = WorkManager.getInstance(appCtx)
                if (scheduleMode == "OFF") {
                    wm.cancelUniqueWork(WORK_NAME)
                } else {
                    val hours = if (scheduleMode == "WEEKLY") 168L else 24L
                    val req = PeriodicWorkRequestBuilder<BackupScheduleWorker>(hours, TimeUnit.HOURS).build()
                    wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
                }
            }
        }
    }
}
