package dev.mi6e4ka.openstore.internal.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponseItem
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.IOException
import net.jpountz.xxhash.XXHashFactory
import java.util.Collections
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
        onInvalidHash: (List<File>, String?, String?) -> Unit = { _, _, _ -> }
    ): () -> Unit {
        val downloadRunner = CoroutineScope(Dispatchers.IO).launch {
            val totalBytesToDownload = files.sumOf { it.size }
            val totalBytesDownloaded = AtomicLong(0)
            val downloadedFiles = Collections.synchronizedList(mutableListOf<File>())
            try {
                val resultingFiles = coroutineScope {
                    files.map { file ->
                        async {
                            val fileName = file.url.split("/").last()
                            val apkFile = getApkFile(fileName)
                            if (!downloadedFiles.contains(apkFile)) {
                                downloadedFiles.add(apkFile)
                            }

                            if (apkFile.exists() && apkFile.length() == file.size) {
                                if (verifyFileHash(apkFile, file.hash)) {
                                    return@async apkFile
                                }
                                apkFile.delete()
                            }

                            val request = Request.Builder().url(file.url).build()
                            val call = okHttpClient.newCall(request)

                            call.execute().use { response ->
                                if (!response.isSuccessful) throw IOException("Ошибка загрузки: ${response.code}")

                                val responseBody =
                                    response.body ?: throw IOException("Пустой ответ от сервера")
                                
                                responseBody.byteStream().use { inputStream ->
                                    FileOutputStream(apkFile).use { outputStream ->
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
                                    }
                                }

                                if (!verifyFileHash(apkFile, file.hash)) {
                                    throw HashMismatchException(apkFile, file.hash)
                                }

                                apkFile
                            }
                        }
                    }.awaitAll()
                }

                withContext(Dispatchers.Main) {
                    onSuccess(resultingFiles)
                }
            } catch (e: Exception) {
                if (e is HashMismatchException) {
                    val expectedHash = formatHashForDisplay(e.expectedHash)
                    val actualHash = computeExistingHash(e.file)
                    val filesCopy = downloadedFiles.toList()
                    withContext(Dispatchers.Main) {
                        onInvalidHash(filesCopy, expectedHash, actualHash)
                    }
                    return@launch
                }

                downloadedFiles.forEach { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (e is CancellationException) {
                        onCancel()
                    } else {
                        onError(e)
                    }
                }
            }
        }

        return {
            downloadRunner.cancel()
        }
    }

    private class HashMismatchException(val file: File, val expectedHash: String) : IOException("Hash mismatch")

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

    private fun verifyFileHash(file: File, expectedHash: String): Boolean {
        val normalizedHash = expectedHash.trim().ifEmpty { return true }
        val expectedBytes = decodeHashValue(normalizedHash) ?: return true
        
        // Only XXH64 is supported now (8 bytes)
        if (expectedBytes.size != 8) return true
        
        val actualBytes = digestFileXXH64(file) ?: return false
        return actualBytes.contentEquals(expectedBytes)
    }

    private fun decodeHashValue(hash: String): ByteArray? {
        return try {
            if (hash.matches(Regex("^[0-9A-Fa-f]+$"))) {
                hash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                Base64.decode(hash, Base64.DEFAULT)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatHashForDisplay(hash: String): String {
        return if (hash.matches(Regex("^[0-9A-Fa-f]+$"))) {
            hash.lowercase()
        } else {
            hash
        }
    }

    private fun computeExistingHash(file: File): String {
        val actualBytes = digestFileXXH64(file) ?: return ""
        return actualBytes.toHexString()
    }

    private fun digestFileXXH64(file: File): ByteArray? {
        return try {
            val factory = XXHashFactory.fastestInstance()
            val hash64 = factory.newStreamingHash64(0L)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    hash64.update(buffer, 0, bytesRead)
                }
            }
            val hashValue = hash64.getValue()
            byteArrayOf(
                (hashValue shr 56).toByte(),
                (hashValue shr 48).toByte(),
                (hashValue shr 40).toByte(),
                (hashValue shr 32).toByte(),
                (hashValue shr 24).toByte(),
                (hashValue shr 16).toByte(),
                (hashValue shr 8).toByte(),
                hashValue.toByte()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun ByteArray.toHexString(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

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
