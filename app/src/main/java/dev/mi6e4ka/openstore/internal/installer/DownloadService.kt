package dev.mi6e4ka.openstore.internal.installer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.mi6e4ka.openstore.MainActivity
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private var currentRequestId: String? = null
    private var currentAppName: String? = null
    private lateinit var apkInstaller: ApkInstaller
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        apkInstaller = ApkInstaller(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> handleStartDownload(intent)
            ACTION_CANCEL_DOWNLOAD -> handleCancelDownload(intent)
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStartDownload(intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val urls = intent.getStringArrayExtra(EXTRA_URLS) ?: return
        val sizes = intent.getLongArrayExtra(EXTRA_SIZES) ?: return
        val hashes = intent.getStringArrayExtra(EXTRA_HASHES) ?: return
        val autoInstall = intent.getBooleanExtra(EXTRA_AUTO_INSTALL, true)

        downloadJob?.cancel()
        currentRequestId = requestId
        currentAppName = appName

        downloadJob = serviceScope.launch {
            try {
                startForeground(NOTIFICATION_ID, buildProgressNotification(appName, 0, 0, 0, 0))

                val items = urls.indices.map { index ->
                    DownloadItem(
                        url = urls[index],
                        size = sizes[index],
                        hash = hashes[index]
                    )
                }

                downloadFiles(
                    requestId = requestId,
                    appName = appName,
                    packageName = packageName,
                    items = items,
                    autoInstall = autoInstall
                )
            } catch (e: CancellationException) {
                currentRequestId = null
                currentAppName = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                AppModule.downloadEventFlow.tryEmit(
                    DownloadEvent.Error(requestId = requestId, throwable = e)
                )
                showFinalNotification(appName, false)
                currentRequestId = null
                currentAppName = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun downloadFiles(
        requestId: String,
        appName: String,
        packageName: String,
        items: List<DownloadItem>,
        autoInstall: Boolean
    ) {
        val totalBytes = items.sumOf { it.size }
        val totalBytesDownloaded = AtomicLong(0)
        val downloadedFiles = mutableListOf<File>()
        var lastUpdateMillis = 0L
        var lastTotalBytes = 0L

        val resultingFiles = try {
            coroutineScope {
                items.map { item ->
                    async {
                        val fileName = item.url.split("/").last()
                        val apkFile = apkInstaller.getApkFile(fileName)

                        synchronized(downloadedFiles) {
                            if (!downloadedFiles.contains(apkFile)) {
                                downloadedFiles.add(apkFile)
                            }
                        }

                        if (apkFile.exists() && apkFile.length() == item.size) {
                            if (apkInstaller.verifyFileHash(apkFile, item.hash)) {
                                totalBytesDownloaded.addAndGet(item.size)
                                return@async apkFile
                            }
                            apkFile.delete()
                        }

                        val request = okhttp3.Request.Builder().url(item.url).build()
                        val call = AppModule.okHttpClient.newCall(request)
                        val job = coroutineContext[Job]!!
                        val cancelHandler = job.invokeOnCompletion { call.cancel() }

                        try {
                            call.execute().use { response ->
                                if (!response.isSuccessful) {
                                    throw IOException("${getString(R.string.loading_error)}: ${response.code}")
                                }

                                val body = response.body ?: throw IOException(getString(R.string.error_empty_response))

                                body.byteStream().use { inputStream ->
                                    FileOutputStream(apkFile).use { outputStream ->
                                        val buffer = ByteArray(16384)
                                        var bytesRead: Int

                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            ensureActive()
                                            outputStream.write(buffer, 0, bytesRead)

                                            val currentTotal = totalBytesDownloaded.addAndGet(bytesRead.toLong())
                                            val now = System.currentTimeMillis()
                                            if (now - lastUpdateMillis > 200) {
                                                val deltaBytes = currentTotal - lastTotalBytes
                                                val deltaTime = now - lastUpdateMillis
                                                lastUpdateMillis = now
                                                lastTotalBytes = currentTotal

                                                val percent = if (totalBytes > 0) {
                                                    (currentTotal * 100 / totalBytes).toInt()
                                                } else 0

                                                val speedBps = if (deltaTime > 0) (deltaBytes * 1000L / deltaTime) else 0L

                                                val notif = buildProgressNotification(
                                                    appName, percent, currentTotal, totalBytes, speedBps
                                                )
                                                notificationManager.notify(NOTIFICATION_ID, notif)

                                                AppModule.downloadEventFlow.tryEmit(
                                                    DownloadEvent.Progress(requestId, percent, currentTotal, totalBytes)
                                                )
                                            }
                                        }
                                        outputStream.flush()
                                    }
                                }

                                if (!apkInstaller.verifyFileHash(apkFile, item.hash)) {
                                    throw HashMismatchException(apkFile, item.hash)
                                }
                                apkFile
                            }
                        } finally {
                            cancelHandler.dispose()
                        }
                    }
                }.awaitAll()
            }
        } catch (e: HashMismatchException) {
            val expectedHash = apkInstaller.formatHashForDisplay(e.expectedHash)
            val actualHash = apkInstaller.computeExistingHash(e.file)
            val filesCopy = synchronized(downloadedFiles) { downloadedFiles.toList() }
            AppModule.downloadEventFlow.tryEmit(
                DownloadEvent.HashMismatch(
                    requestId = requestId,
                    files = filesCopy,
                    expectedHash = expectedHash,
                    actualHash = actualHash
                )
            )
            showFinalNotification(appName, false)
            currentRequestId = null
            currentAppName = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        AppModule.downloadEventFlow.tryEmit(
            DownloadEvent.Progress(requestId, 100, totalBytes, totalBytes)
        )

        if (autoInstall) {
            val installNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(appName)
                .setContentText(getString(R.string.installing))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, installNotif)

            apkInstaller.installApp(
                packageName = packageName,
                apkFiles = resultingFiles,
                onSessionFinished = { success ->
                    if (success) {
                        apkInstaller.cleanApkFiles(resultingFiles.map { it.name })
                    }
                    AppModule.downloadEventFlow.tryEmit(
                        DownloadEvent.Success(requestId = requestId, packageName = packageName, files = resultingFiles)
                    )
                    showFinalNotification(appName, success)
                    currentRequestId = null
                    currentAppName = null
                    stopForegroundCompat(false)
                    stopSelf()
                },
                onError = { error ->
                    AppModule.downloadEventFlow.tryEmit(
                        DownloadEvent.Error(requestId = requestId, throwable = error)
                    )
                    showFinalNotification(appName, false)
                    currentRequestId = null
                    currentAppName = null
                    stopForegroundCompat(false)
                    stopSelf()
                }
            )
        } else {
            AppModule.downloadEventFlow.tryEmit(
                DownloadEvent.Success(requestId = requestId, packageName = packageName, files = resultingFiles)
            )
            showFinalNotification(appName, true)
            currentRequestId = null
            currentAppName = null
            stopForegroundCompat(true)
            stopSelf()
        }
    }

    private fun handleCancelDownload(intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        if (requestId == null || requestId == currentRequestId) {
            downloadJob?.cancel()
            currentRequestId?.let { id ->
                AppModule.downloadEventFlow.tryEmit(DownloadEvent.Canceled(id))
            }
            currentRequestId = null
            currentAppName = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(
        appName: String,
        percent: Int,
        downloaded: Long,
        total: Long,
        speedBps: Long
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_REQUEST_ID, currentRequestId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, currentRequestId?.hashCode() ?: 1, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val totalMb = total.toFloat() / (1024 * 1024)
        val downloadedMb = downloaded.toFloat() / (1024 * 1024)
        val speedText = if (speedBps > 0) {
            formatSpeed(speedBps)
        } else ""

        val remainingBytes = total - downloaded
        val etaText = if (speedBps > 0 && remainingBytes > 0) {
            val etaSecs = remainingBytes / speedBps
            formatEta(etaSecs)
        } else ""

        val contentText = buildString {
            append("%.1f / %.1f MB".format(downloadedMb, totalMb))
            if (speedText.isNotEmpty()) append(" • $speedText")
            if (etaText.isNotEmpty()) append(" • $etaText")
        }

        val bigText = buildString {
            appendLine(getString(R.string.notification_app, appName))
            appendLine()
            appendLine("%.1f / %.1f MB (%d%%)".format(downloadedMb, totalMb, percent))
            if (speedText.isNotEmpty()) appendLine(getString(R.string.notification_speed, speedText))
            if (etaText.isNotEmpty()) appendLine(getString(R.string.notification_eta, etaText))
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(appName)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), cancelPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun showFinalNotification(appName: String, success: Boolean) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(appName)
            .setContentText(
                if (success) getString(R.string.notification_download_complete)
                else getString(R.string.notification_download_failed)
            )
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_warning
            )
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundCompat(keepNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(if (keepNotification) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(!keepNotification)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000f)
            bytesPerSec >= 1_000 -> "%.0f KB/s".format(bytesPerSec / 1_000f)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatEta(seconds: Long): String {
        return when {
            seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
            seconds >= 60 -> "%d:%02d".format(seconds / 60, seconds % 60)
            else -> "%d ${getString(R.string.seconds_short)}".format(seconds)
        }
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "dev.mi6e4ka.openstore.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "dev.mi6e4ka.openstore.action.CANCEL_DOWNLOAD"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_URLS = "urls"
        const val EXTRA_SIZES = "sizes"
        const val EXTRA_HASHES = "hashes"
        const val EXTRA_AUTO_INSTALL = "auto_install"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "openstore_download_channel"
        private const val TAG = "DownloadService"
    }
}

internal data class DownloadItem(
    val url: String,
    val size: Long,
    val hash: String
)

internal class HashMismatchException(val file: File, val expectedHash: String) : IOException("Hash mismatch")
