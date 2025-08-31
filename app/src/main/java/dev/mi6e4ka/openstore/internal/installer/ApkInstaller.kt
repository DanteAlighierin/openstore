package dev.mi6e4ka.openstore.internal.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException

class ApkInstaller(private val context: Context) {

    companion object {
        private val okHttpClient = OkHttpClient()
    }

    suspend fun downloadApkWithProgress(
        url: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val fileName = url.split("/").last()

        val apkFile = getApkFile(fileName)

        // Проверяем, не скачан ли уже файл
        if (isApkAlreadyDownloaded(fileName)) {
            return@withContext apkFile
        }

        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Ошибка загрузки: ${response.code}")
            }

            val responseBody = response.body
            if (responseBody == null) {
                throw IOException("Пустой ответ от сервера")
            }

            downloadToFileWithProgress(responseBody, apkFile, onProgress)
        }

        apkFile
    }

    private fun downloadToFileWithProgress(
        responseBody: ResponseBody,
        file: File,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ) {
        val totalBytes = responseBody.contentLength()
        var downloadedBytes = 0L

        responseBody.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // Вычисляем прогресс
                    val progress = if (totalBytes > 0) {
                        (downloadedBytes * 100 / totalBytes).toInt()
                    } else {
                        0
                    }

                    // Отправляем прогресс
                    onProgress(progress, downloadedBytes, totalBytes)
                }
            }
        }
    }

    fun isApkAlreadyDownloaded(fileName: String): Boolean {
        val apkFile = getApkFile(fileName)
        return apkFile.exists() && apkFile.length() > 0
    }

    fun getApkFile(fileName: String): File {
        return File(context.externalCacheDir, fileName)
    }

    fun deleteApkFile(fileName: String) {
        val apkFile = getApkFile(fileName)
        if (apkFile.exists()) {
            apkFile.delete()
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
//                //sdkAbove(sdk = Build.VERSION_CODES.S) {
//                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
//                //}
//                //sdkAbove(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//                    setRequestUpdateOwnership(true)
//                //}
        }

    fun installApk(context: Context, apkFile: File, installerCallback: PackageInstaller.SessionCallback) {
        val id = installer.createSession(sessionParams)
        installer.registerSessionCallback(
            installerCallback,
            Handler(Looper.getMainLooper())
        )

        val session = installer.openSession(id)

        session.use { activeSession ->
            val sizeBytes = apkFile.length()
            apkFile.inputStream().use { fileStream ->
                activeSession.openWrite(apkFile.name, 0, sizeBytes).use { outputStream ->
                    fileStream.copyTo(outputStream)
                    activeSession.fsync(outputStream)
                }
            }
            val pendingIntent = PendingIntent.getBroadcast(context, id, intent, flags)
            activeSession.commit(pendingIntent.intentSender)
        }

    }

}