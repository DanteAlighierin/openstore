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
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApkInstaller(private val context: Context) {

    companion object {
        private val okHttpClient = OkHttpClient()
    }

    fun downloadApkWithProgress(
        url: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onCancel: () -> Unit,
    ): () -> Unit {

        val fileName = url.split("/").last()
        val apkFile = getApkFile(fileName)

        // Проверяем, не скачан ли уже файл
        if (isApkAlreadyDownloaded(fileName)) {
            onSuccess(apkFile)
            return {} // Пустая функция отмены
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val call = okHttpClient.newCall(request)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Ошибка загрузки: ${response.code}")
                    }

                    val responseBody = response.body
                    if (responseBody == null) {
                        throw IOException("Пустой ответ от сервера")
                    }

                    val contentLength = responseBody.contentLength()
                    val inputStream = responseBody.byteStream()
                    val outputStream = FileOutputStream(apkFile)

                    val buffer = ByteArray(8192)
                    var downloaded: Long = 0
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (call.isCanceled()) {
                            throw IOException("Canceled")
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        downloaded += bytesRead.toLong()

                        val percent = if (contentLength > 0) {
                            (downloaded * 100 / contentLength).toInt()
                        } else {
                            0
                        }

                        withContext(Dispatchers.Main) {
                            onProgress(percent, downloaded, contentLength)
                        }
                    }

                    outputStream.flush()
                    inputStream.close()
                    outputStream.close()

                    withContext(Dispatchers.Main) {
                        onSuccess(apkFile)
                    }
                }
            } catch (e: IOException) {
                if (apkFile.exists()) {
                    apkFile.delete()
                }
                if (e.message == "Canceled") {
                    onCancel()
                } else {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            } catch (e: Exception) {
                if (apkFile.exists()) {
                    apkFile.delete()
                }
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }

        return {
            call.cancel()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setRequestUpdateOwnership(true)
            }
        }

    fun installApk(context: Context, apkFile: File, installerCallback: PackageInstaller.SessionCallback, onError: (e: Exception) -> Unit) {
        try {
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
        } catch (e: Exception) {
            onError(e)
        }

    }

}