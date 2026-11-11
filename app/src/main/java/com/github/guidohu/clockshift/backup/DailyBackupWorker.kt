package com.github.guidohu.clockshift.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.guidohu.clockshift.container
import com.github.guidohu.clockshift.notify.Notifications

/**
 * Writes the daily backup (Pro). A storage app that is briefly unavailable gets a few retries; a target the
 * app can no longer reach is reported right away, since retrying cannot fix it.
 */
class DailyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        if (!container.pro.isPro.value || !container.backup.daily.value.enabled) return Result.success()
        return when (container.backup.runDaily()) {
            null -> {
                Notifications.dismiss(applicationContext, Notifications.ID_BACKUP)
                Result.success()
            }

            BackupError.WriteFailed -> if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Notifications.showBackupFailed(applicationContext, BackupError.WriteFailed)
                Result.success() // keeps the periodic job alive for tomorrow
            }

            BackupError.NoAccess -> {
                Notifications.showBackupFailed(applicationContext, BackupError.NoAccess)
                Result.success()
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
