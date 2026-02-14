package dev.mi6e4ka.openstore.internal.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponseItem
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

class ApkInstaller(private val context: Context) {

    companion object {
        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun downloadFilesWithProgress(
        files: List<DownloadLinkResponseItem>,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit,
        onSuccess: (List<File>) -> Unit,
        onError: (Throwable) -> Unit,
        onCancel: () -> Unit,
    ): () -> Unit {
        val downloadRunner = CoroutineScope(Dispatchers.IO).launch {
            val totalBytesToDownload = files.sumOf { it.size }
            val totalBytesDownloaded = AtomicLong(0)
            val downloadedFiles = mutableListOf<File>()
            try {
                val downloadJobs = files.map { file ->
                    async {
                        val fileName = file.url.split("/").last()
                        val apkFile = getApkFile(fileName)
                        downloadedFiles.add(apkFile)

                        if (apkFile.exists() && apkFile.length() == file.size) {
                            return@async apkFile
                        }

                        val request = Request.Builder().url(file.url).build()
                        val call = okHttpClient.newCall(request)

                        call.execute().use { response ->
                            if (!response.isSuccessful) throw IOException("Ошибка загрузки: ${response.code}")

                            val responseBody =
                                response.body ?: throw IOException("Пустой ответ от сервера")
                            val inputStream = responseBody.byteStream()
                            val outputStream = FileOutputStream(apkFile)

                            val buffer = ByteArray(8192)
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (call.isCanceled()) {
                                    throw IOException("Canceled")
                                }

                                outputStream.write(buffer, 0, bytesRead)

                                val newTotalDownloaded =
                                    totalBytesDownloaded.addAndFetch(bytesRead.toLong())
                                val percent = if (totalBytesToDownload > 0) {
                                    (newTotalDownloaded * 100 / totalBytesToDownload).toInt()
                                } else {
                                    0
                                }

                                withContext(Dispatchers.Main) {
                                    onProgress(percent, newTotalDownloaded, totalBytesToDownload)
                                }
                            }

                            outputStream.flush()
                            inputStream.close()
                            outputStream.close()

                            apkFile
                        }
                    }
                }
                val resultingFiles = downloadJobs.awaitAll()

                withContext(Dispatchers.Main) {
                    onSuccess(resultingFiles)
                }
            } catch (e: Exception) {
                downloadedFiles.forEach { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
                if (e is CancellationException) {
                    onCancel()
                } else {
                    onError(e)
                }
            }
        }

        return {
            downloadRunner.cancel()
        }
    }

    fun getApkFile(fileName: String): File {
        return File(context.externalCacheDir ?: context.cacheDir, fileName)
    }

    fun cleanApkFiles(files: List<String>) {
        files.forEach {
            val apkFile = getApkFile(it)
            if (apkFile.exists()) {
                apkFile.delete()
            }
        }
    }
    private val installer = context.packageManager.packageInstaller
    private val flags = PendingIntent.FLAG_MUTABLE
    private val intent = Intent(context, PackageInstallerStatusReceiver::class.java)

    fun installApp(
        packageName: String,
        apkFiles: List<File>,
        onSessionFinished: (Boolean) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val installerType = prefs.getString("installer_type", "session")

        when (installerType) {
            "session" -> {
                installViaSession(apkFiles, onSessionFinished, onError)
            }
            "intent" -> {
                installViaIntent(packageName, apkFiles, onError)
            }
        }
    }

    private fun installViaSession(
        apkFiles: List<File>,
        onSessionFinished: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val sessionParams =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setRequestUpdateOwnership(true)
                    }
                }
            val sessionId = installer.createSession(sessionParams)
            installer.registerSessionCallback(object : PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) {}
                override fun onBadgingChanged(sessionId: Int) {}
                override fun onActiveChanged(sessionId: Int, active: Boolean) {}
                override fun onProgressChanged(sessionId: Int, progress: Float) {}
                override fun onFinished(id: Int, success: Boolean) {
                    if (id == sessionId) {
                        onSessionFinished(success)
                        installer.unregisterSessionCallback(this)
                    }
                }
            }, Handler(Looper.getMainLooper()))

            val session = installer.openSession(sessionId)
            session.use { activeSession ->
                apkFiles.forEach { file ->
                    val sizeBytes = file.length()
                    file.inputStream().use { fileStream ->
                        activeSession.openWrite(file.name, 0, sizeBytes).use { outputStream ->
                            fileStream.copyTo(outputStream)
                            activeSession.fsync(outputStream)
                        }
                    }
                }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                activeSession.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            Log.e("ApkInstaller", "Error during session install", e)
            onError(e)
        }
    }

    private fun installViaIntent(
        packageName: String,
        apkFiles: List<File>,
        onError: (Exception) -> Unit
    ) {
        try {
            val apkUri: Uri
            if (apkFiles.size == 1) {
                apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFiles[0]
                )
            } else {
                Toast.makeText(context, R.string.intent_installer_split_apk_build, Toast.LENGTH_SHORT)
                    .show()
                val apksFile = File(context.externalCacheDir ?: context.cacheDir, "$packageName.apks")
                buildSplitApk(apkFiles, apksFile)
                apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apksFile
                )
            }
            Toast.makeText(context, R.string.intent_installer_warning, Toast.LENGTH_SHORT)
                .show()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("ApkInstaller", "Error during intent install", e)
            onError(e)
        }
    }

    private fun buildSplitApk(apkFiles: List<File>, outputZip: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
            apkFiles.forEach { file ->
                FileInputStream(file).use { fis ->
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }
}
