package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.HisabPulseApp
import com.example.core.DateUtils
import java.io.File
import java.util.concurrent.TimeUnit

class EncryptedDailyBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? HisabPulseApp ?: return Result.failure()
            val backupRepo = app.container.backupRepository
            val backupDir = File(applicationContext.filesDir, "encrypted_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val dateStr = DateUtils.formatDate(System.currentTimeMillis()).replace(" ", "_").replace(":", "-")
            val fileName = "HisabPulse_Encrypted_Backup_$dateStr.bizmgr"
            val targetFile = File(backupDir, fileName)

            val bytes = backupRepo.createBackupBytes(encrypt = true, passphrase = "")
            targetFile.writeBytes(bytes)
            showCompletionNotification(fileName)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showCompletionNotification(fileName: String) {
        val channelId = "hisabpulse_backup_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Automated Encrypted Backups",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily background AES-256 encrypted database backups"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Encrypted Backup Completed")
            .setContentText("Daily secure backup saved: $fileName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(2001, notification)
        }
    }

    companion object {
        private const val WORK_NAME = "hisabpulse_daily_encrypted_backup"

        fun schedule(context: Context) {
            runCatching {
                val appCtx = context.applicationContext
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(appCtx, Configuration.Builder().build())
                }
                val request = PeriodicWorkRequestBuilder<EncryptedDailyBackupWorker>(24, TimeUnit.HOURS)
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
