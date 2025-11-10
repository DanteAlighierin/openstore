package dev.mi6e4ka.openstore.ui.screen.details

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.data.model.AppRatingResponseBody
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponseItem
import dev.mi6e4ka.openstore.data.model.ItemDetails
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
import dev.mi6e4ka.openstore.internal.installer.PackageInstallerStatusReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri

data class DetailsState(
    val isLoading: Boolean = true,
    val item: ItemDetails? = null,
    val rating: AppRatingResponseBody? = null,
    val error: String? = null,
    val appFiles: List<DownloadLinkResponseItem>? = null,
    val plainApkUrl: String? = null,
    val splitOnly: Boolean? = null,
    val installStatus: InstallStatus = InstallStatus.Idle,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadCurrentBytes: Long = 0,
    val downloadTotalBytes: Long = 0,
    val isInstalled: Boolean = false,
    val isNeedUpgrade: Boolean = false,
    val isSupported: Boolean = true,
)

sealed class InstallStatus {
    object Idle : InstallStatus()
    object Pending : InstallStatus()
    object Success : InstallStatus()
}

class DetailsViewModel(private val appPlatform: String, private val apkInstaller: ApkInstaller) : ViewModel() {
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()
    private var cancelDownload: (() -> Unit)? = null

    fun loadDetails(context: Context, itemId: String) {
        if (_state.value.item != null) {
            return
        }
        Log.d(null, "Initial loading")
        viewModelScope.launch {
            var item: ItemDetails? = null
            // основная информация о приложении
            try {
                item = AppModule.searchRepository.getItemDetails(itemId, appPlatform)
                //
                val packageInfo  = getPackageInfo(context, itemId)
                _state.value = DetailsState(isLoading = true, isInstalled = packageInfo != null)
                if (packageInfo != null) {
                    val installedVersionCode = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    })
                    if (installedVersionCode < item.versionCode) {
                        _state.update { it.copy(isNeedUpgrade = true) }
                    }
                }
                //
                _state.update { it.copy(isSupported = Build.VERSION.SDK_INT >= item.minSdkVersion) }
                _state.update{ it.copy(item = item) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
            // пробуем получить все файлы приложения (включая split)
            try {
                if (item == null) return@launch
                val appFiles = AppModule.searchRepository.getAppFiles(item.appId, firstInstall = true, supportedAbis = Build.SUPPORTED_ABIS.toList(), withoutSplits = false, deviceType = appPlatform)
                _state.update{ it.copy(appFiles = appFiles) }
            } catch (e: Exception) {}
            // пробуем получить файлы приложения указав что нам не нужны split apk
            try {
                if (item == null) return@launch
                val singleApkUrl = AppModule.searchRepository.getAppFiles(item.appId, firstInstall = true, supportedAbis = Build.SUPPORTED_ABIS.toList(), withoutSplits = true, deviceType = appPlatform).firstOrNull()?.url
                _state.update{ it.copy(plainApkUrl = singleApkUrl, splitOnly = singleApkUrl == null) }
            } catch (e: Exception) {}
            // рейтинг
            try {
                val rating = AppModule.searchRepository.getAppRating(itemId)
                _state.update{ it.copy(rating = rating) }
            } catch (e: Exception) {}

            _state.update { it.copy(isLoading = false) }
        }
    }

    fun downloadAndInstallApp(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true, downloadProgress = 0, downloadTotalBytes = 0, downloadCurrentBytes = 0) }
            Log.d(null, "download apk " + state.value.appFiles)
            cancelDownload = apkInstaller.downloadFilesWithProgress(
                state.value.appFiles!!,
                onProgress = { percent, downloaded, total ->
                    _state.update { it.copy(downloadProgress = percent, downloadCurrentBytes = downloaded, downloadTotalBytes = total) }
                },
                onSuccess = { apkFiles ->
                    installApp(context, apkFiles)
                },
                onError = { error ->
                    if (error.message?.contains("timeout") == true) {
                        Toast.makeText(context, R.string.timeout, Toast.LENGTH_SHORT).show()
                    } else if (error.message?.contains("Failed to allocate") == true) {
                        Toast.makeText(context, R.string.insufficient_memory, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.loading_error, Toast.LENGTH_SHORT).show()
                    }
                    _state.update { it.copy(isDownloading = false) }
                },
                onCancel = {
                    _state.update { it.copy(isDownloading = false) }
                }
            )
        }
    }

    fun cancelDownload() {
        cancelDownload?.invoke()
        cancelDownload = null
    }

    private fun installApp(context: Context, apkFiles: List<File>) {
        viewModelScope.launch {
            _state.update { it.copy(installStatus = InstallStatus.Pending) }
            val installerCallback = object : PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) {}
                override fun onBadgingChanged(sessionId: Int) {}
                override fun onActiveChanged(sessionId: Int, active: Boolean) {}
                override fun onProgressChanged(sessionId: Int, progress: Float) {}
                override fun onFinished(sessionId: Int, success: Boolean) {
                    _state.update { it.copy(installStatus = InstallStatus.Success, isDownloading = false)}
                    if (success) {
                        _state.update { it.copy(isInstalled = true, isNeedUpgrade = false) }
                        apkInstaller.cleanApkFiles(apkFiles.map {it.name})
                    }
                }
            }
            apkInstaller.installSplitApk(context, apkFiles, installerCallback, onError = { error ->
                if (error.message?.contains("Failed to allocate") == true) {
                    Toast.makeText(context, R.string.insufficient_memory, Toast.LENGTH_SHORT).show()
                } else {
                    println("apk installer $error")
                    Toast.makeText(context, R.string.installing_error, Toast.LENGTH_SHORT).show()
                }
                _state.update { it.copy(installStatus = InstallStatus.Idle, isDownloading = false) }
            })
        }
    }

    private fun getPackageInfo(context: Context, packageName: String): PackageInfo? {
        Log.d(null, "try to check package $packageName")
        return try {
            val packageInfo : PackageInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            }
            Log.d(null, "try")
            packageInfo
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(null, "false")
            null
        } catch (e: Exception) {
            null
        }
    }
    fun runApplication(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, R.string.failed_to_open_app, Toast.LENGTH_SHORT).show()
        }
    }
    fun deleteApplication(context: Context, packageName: String) {
        val intent = Intent(context, PackageInstallerStatusReceiver::class.java)
        intent.putExtra(PackageInstallerStatusReceiver.ACTION_UNINSTALL, true)
        val pendingIntent = PendingIntent.getBroadcast(context, -1, intent, PendingIntent.FLAG_MUTABLE)
        context.packageManager.packageInstaller.uninstall(packageName, pendingIntent.intentSender)
    }

    fun updateInstallStatus(isInstalled: Boolean) {
        _state.update { it.copy(isInstalled = isInstalled) }
    }

    fun downloadFileToFolder(context: Context) {
        val fileName = "${state.value.item?.packageName}-${state.value.item?.versionName}.apk"
        val request = DownloadManager.Request(state.value.plainApkUrl?.toUri())
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(DownloadManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }
        downloadManager.enqueue(request)
        Toast.makeText(context, R.string.download_has_started, Toast.LENGTH_SHORT).show()
    }

    fun copyTextToCB(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text)
        clipboard.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(context, R.string.copied_to_buffer, Toast.LENGTH_SHORT).show()
        }
    }
}

class DetailsViewModelFactory(
    private val itemId: String,
    private val apkInstaller: ApkInstaller
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailsViewModel(itemId, apkInstaller) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
