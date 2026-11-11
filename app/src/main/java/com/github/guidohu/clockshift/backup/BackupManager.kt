package com.github.guidohu.clockshift.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.guidohu.clockshift.data.AppDatabase
import com.github.guidohu.clockshift.domain.fromLocalMinutes
import com.github.guidohu.clockshift.domain.toLocalMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Where daily backups go: dated files in a folder, or one file that is replaced each time. */
enum class BackupTargetKind { Folder, File }

/** Why the last daily backup failed. */
enum class BackupError {
    /** The folder or file is gone, or the app lost access to it. */
    NoAccess,

    /** Writing failed, e.g. the storage app was unavailable; it is retried. */
    WriteFailed,
}

data class DailyBackupState(
    val enabled: Boolean = false,
    val kind: BackupTargetKind = BackupTargetKind.Folder,
    val targetUri: Uri? = null,
    val targetName: String? = null,
    val lastSuccess: LocalDateTime? = null,
    val lastError: BackupError? = null,
    val lastErrorAt: LocalDateTime? = null,
)

/**
 * Backup and restore. A manual backup or restore goes through a file the user picks (free). The daily
 * backup (Pro) runs as a WorkManager job and writes through the Storage Access Framework, so the target can
 * be any folder on the phone or any storage app that offers one (Nextcloud, ownCloud, …). Apps that do
 * not share folders, like Google Drive, can still take a single file that is replaced every day.
 */
class BackupManager(private val context: Context, private val database: AppDatabase) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _daily = MutableStateFlow(readState())

    /** Daily backup configuration and last result. */
    val daily: StateFlow<DailyBackupState> = _daily.asStateFlow()

    // --- snapshot and restore ------------------------------------------------------------------

    suspend fun snapshot(now: LocalDateTime = LocalDateTime.now()): BackupData = database.withTransaction {
        BackupData(
            createdAt = now.withSecond(0).withNano(0),
            appVersion = appVersion(),
            settings = database.settingsDao().get(),
            scheduleDays = database.scheduleDao().getDays(),
            scheduleBreaks = database.scheduleDao().getBreaks(),
            entries = database.entryDao().getAll(),
            daysOff = database.dayOffDao().getAll(),
        )
    }

    /** Replaces all data with [data] in one transaction: either everything is restored or nothing changes. */
    suspend fun restore(data: BackupData) = database.withTransaction {
        database.entryDao().deleteAll()
        database.dayOffDao().deleteAll()
        database.scheduleDao().deleteAllDays()
        database.scheduleDao().deleteAllBreaks()
        database.entryDao().insertAll(data.entries)
        database.dayOffDao().insertAll(data.daysOff)
        database.scheduleDao().insertDays(data.scheduleDays)
        database.scheduleDao().insertBreaks(data.scheduleBreaks)
        data.settings?.let { database.settingsDao().upsert(it) }
    }

    suspend fun writeTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val json = BackupCodec.encode(snapshot())
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: error("No stream")
        }.isSuccess
    }

    /** Reads and checks a backup file without changing anything. */
    suspend fun readFrom(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: throw BackupFormatException("Cannot read file")
        BackupCodec.decode(text)
    }

    fun fileName(now: LocalDateTime = LocalDateTime.now()): String = "$FILE_PREFIX${now.toLocalDate()}$FILE_SUFFIX"

    // --- daily backup configuration --------------------------------------------------------------

    fun setDailyTarget(uri: Uri, kind: BackupTargetKind) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        // Let go of the previous target, unless it is the same one.
        _daily.value.targetUri?.takeIf { it != uri }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        prefs.edit {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_KIND, kind.name)
            putString(KEY_URI, uri.toString())
            putString(KEY_NAME, displayName(uri, kind))
            remove(KEY_ERROR)
            remove(KEY_ERROR_AT)
        }
        refreshState()
    }

    fun setDailyEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        refreshState()
    }

    /**
     * Schedules or cancels the daily job to match the settings and the Pro status. Called when either
     * changes. The job runs about once a day while the battery is not low.
     */
    fun sync(isPro: Boolean) {
        val work = WorkManager.getInstance(context)
        val state = _daily.value
        if (isPro && state.enabled && state.targetUri != null) {
            val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK_DAILY)
        }
    }

    /** Runs the daily backup right away, e.g. to try a newly chosen folder. */
    fun runNow() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DailyBackupWorker>().build())
    }

    // --- daily backup run -----------------------------------------------------------------------

    /** Writes today's backup to the configured target. Returns null on success. */
    internal suspend fun runDaily(): BackupError? = withContext(Dispatchers.IO) {
        val state = _daily.value
        val uri = state.targetUri ?: return@withContext BackupError.NoAccess
        val bytes = BackupCodec.encode(snapshot()).toByteArray(Charsets.UTF_8)
        val error = try {
            when (state.kind) {
                BackupTargetKind.File -> writeDocument(uri, bytes)
                BackupTargetKind.Folder -> writeToFolder(uri, bytes)
            }
            null
        } catch (e: SecurityException) {
            BackupError.NoAccess
        } catch (e: java.io.FileNotFoundException) {
            BackupError.NoAccess
        } catch (e: IllegalArgumentException) {
            BackupError.NoAccess
        } catch (e: java.io.IOException) {
            BackupError.WriteFailed
        }
        recordResult(error)
        error
    }

    private fun writeDocument(uri: Uri, bytes: ByteArray) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No stream")
        stream.use { it.write(bytes) }
    }

    /** Today's file is created (or replaced if the job runs twice a day); only the newest [KEEP] are kept. */
    private fun writeToFolder(tree: Uri, bytes: ByteArray) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val existing = listBackups(tree)
        val name = fileName()
        val target = existing.firstOrNull { it.second == name }?.first
            ?: DocumentsContract.createDocument(resolver, parent, MIME, name)
            ?: throw java.io.IOException("Cannot create $name")
        writeDocument(target, bytes)

        (existing.filter { it.second != name } + (target to name))
            .sortedByDescending { it.second }
            .drop(KEEP)
            .forEach { (uri, _) -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
    }

    /** Backups in the folder as (document uri, file name). */
    private fun listBackups(tree: Uri): List<Pair<Uri, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val result = mutableListOf<Pair<Uri, String>>()
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)) {
                    result += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)) to name
                }
            }
        }
        return result
    }

    private fun recordResult(error: BackupError?) {
        val now = LocalDateTime.now().toLocalMinutes()
        prefs.edit {
            if (error == null) {
                putLong(KEY_SUCCESS, now)
                remove(KEY_ERROR)
                remove(KEY_ERROR_AT)
            } else {
                putString(KEY_ERROR, error.name)
                putLong(KEY_ERROR_AT, now)
            }
        }
        refreshState()
    }

    // --- state --------------------------------------------------------------------------------

    private fun refreshState() {
        _daily.value = readState()
    }

    private fun readState() = DailyBackupState(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        kind = prefs.getString(KEY_KIND, null)?.let { runCatching { BackupTargetKind.valueOf(it) }.getOrNull() } ?: BackupTargetKind.Folder,
        targetUri = prefs.getString(KEY_URI, null)?.let(Uri::parse),
        targetName = prefs.getString(KEY_NAME, null),
        lastSuccess = if (prefs.contains(KEY_SUCCESS)) prefs.getLong(KEY_SUCCESS, 0L).fromLocalMinutes() else null,
        lastError = prefs.getString(KEY_ERROR, null)?.let { runCatching { BackupError.valueOf(it) }.getOrNull() },
        lastErrorAt = if (prefs.contains(KEY_ERROR_AT)) prefs.getLong(KEY_ERROR_AT, 0L).fromLocalMinutes() else null,
    )

    private fun displayName(uri: Uri, kind: BackupTargetKind): String? = runCatching {
        val document = if (kind == BackupTargetKind.Folder) {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }
        context.contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    companion object {
        const val MIME = "application/json"
        const val FILE_PREFIX = "clockshift-backup-"
        const val FILE_SUFFIX = ".json"

        /** Daily backups kept in a folder. */
        const val KEEP = 7

        private const val PREFS = "backup"
        private const val KEY_ENABLED = "daily_enabled"
        private const val KEY_KIND = "daily_kind"
        private const val KEY_URI = "daily_uri"
        private const val KEY_NAME = "daily_name"
        private const val KEY_SUCCESS = "last_success"
        private const val KEY_ERROR = "last_error"
        private const val KEY_ERROR_AT = "last_error_at"
        private const val WORK_DAILY = "daily_backup"
        private const val WORK_NOW = "backup_now"
    }
}
