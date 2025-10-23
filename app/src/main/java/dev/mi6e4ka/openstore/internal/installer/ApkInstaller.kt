package dev.mi6e4ka.openstore.internal.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import java.io.IOException
import java.util.concurrent.TimeUnit
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

                // Если мы здесь, значит все файлы скачаны успешно
                withContext(Dispatchers.Main) {
                    onSuccess(resultingFiles)
                }
            } catch (e: Exception) {
                downloadedFiles.forEach { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }

                // Обрабатываем ошибки
                if (e is CancellationException) {
                    withContext(Dispatchers.Main) { onCancel() }
                } else {
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }

        return {
            downloadRunner.cancel()
        }
    }

    fun isApkAlreadyDownloaded(fileName: String): Boolean {
        val apkFile = getApkFile(fileName)
        return apkFile.exists() && apkFile.length() > 0
    }

    fun getApkFile(fileName: String): File {
        return File(context.externalCacheDir, fileName)
    }

    fun cleanApkFiles(files: List<String>) {
        files.forEach {
            val apkFile = getApkFile(it)
            if (apkFile.exists()) {
                apkFile.delete()
            }
        }
    }

    fun getApkFileSize(fileName: String): Long {
        return getApkFile(fileName).length()
    }

    private val installer = context.packageManager.packageInstaller
    private val flags = PendingIntent.FLAG_MUTABLE
    private val intent = Intent(context, PackageInstallerStatusReceiver::class.java)
    private val sessionParams =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setRequestUpdateOwnership(true)
            }
        }

    fun installSplitApk(context: Context, apkFiles: List<File>, installerCallback: PackageInstaller.SessionCallback, onError: (e: Exception) -> Unit) {
        val sessionId = installer.createSession(sessionParams)
        installer.registerSessionCallback(
            installerCallback,
            Handler(Looper.getMainLooper())
        )

        val session = installer.openSession(sessionId)
        try {
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
            installer.abandonSession(sessionId)
            onError(e)
        }
    }

}