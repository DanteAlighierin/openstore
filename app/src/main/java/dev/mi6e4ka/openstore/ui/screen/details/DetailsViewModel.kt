package dev.mi6e4ka.openstore.ui.screen.details

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.data.model.AppRatingResponseBody
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponseItem
import dev.mi6e4ka.openstore.data.model.ItemDetails
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
import dev.mi6e4ka.openstore.internal.installer.DownloadEvent
import dev.mi6e4ka.openstore.internal.installer.DownloadService
import dev.mi6e4ka.openstore.internal.installer.PackageInstallerStatusReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DetailsState(
    val isLoading: Boolean = true,
    val item: ItemDetails? = null,
    val rating: AppRatingResponseBody? = null,
    val error: String? = null,
    val appFiles: List<DownloadLinkResponseItem>? = null,
    val plainApkUrl: String? = null,
    val plainApk: DownloadLinkResponseItem? = null,
    val splitOnly: Boolean? = null,
    val installStatus: InstallStatus = InstallStatus.Idle,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadCurrentBytes: Long = 0,
    val downloadTotalBytes: Long = 0,
    val isFolderDownloading: Boolean = false,
    val folderDownloadProgress: Int = 0,
    val isInstalled: Boolean = false,
    val isNeedUpgrade: Boolean = false,
    val isSupported: Boolean = true,
    val installerPackageName: String? = null,
    val invalidDownloadedApkFiles: List<File>? = null,
    val invalidDownloadMessage: String? = null,
    val invalidExpectedHash: String? = null,
    val invalidActualHash: String? = null
)

sealed class InstallStatus {
    object Idle : InstallStatus()
    object Pending : InstallStatus()
    object Success : InstallStatus()
}

class DetailsViewModel(private val appPlatform: String, private val apkInstaller: ApkInstaller) : ViewModel() {
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            AppModule.downloadEventFlow.events.collect { event ->
                val packageName = _state.value.item?.packageName ?: return@collect
                val requestId = "install_$packageName"
                val folderRequestId = "folder_$packageName"

                when (event) {
                    is DownloadEvent.Progress -> {
                        if (event.requestId == requestId) {
                            _state.update {
                                it.copy(
                                    isDownloading = true,
                                    downloadProgress = event.percent,
                                    downloadCurrentBytes = event.downloadedBytes,
                                    downloadTotalBytes = event.totalBytes,
                                    installStatus = if (event.percent == 100) InstallStatus.Pending else it.installStatus
                                )
                            }
                        } else if (event.requestId == folderRequestId) {
                            _state.update {
                                it.copy(
                                    isFolderDownloading = true,
                                    folderDownloadProgress = event.percent
                                )
                            }
                        }
                    }
                    is DownloadEvent.Success -> {
                        if (event.requestId == requestId) {
                            _state.update {
                                it.copy(
                                    isDownloading = false,
                                    installStatus = InstallStatus.Success,
                                    isInstalled = true,
                                    isNeedUpgrade = false,
                                    installerPackageName = it.installerPackageName
                                )
                            }
                        }
                    }
                    is DownloadEvent.Error -> {
                        if (event.requestId == requestId) {
                            _state.update { it.copy(isDownloading = false, installStatus = InstallStatus.Idle) }
                            handleDownloadError(event.throwable)
                        } else if (event.requestId == folderRequestId) {
                            _state.update { it.copy(isFolderDownloading = false, folderDownloadProgress = 0) }
                            handleDownloadError(event.throwable)
                        }
                    }
                    is DownloadEvent.Canceled -> {
                        if (event.requestId == requestId) {
                            _state.update { it.copy(isDownloading = false, installStatus = InstallStatus.Idle) }
                        } else if (event.requestId == folderRequestId) {
                            _state.update { it.copy(isFolderDownloading = false, folderDownloadProgress = 0) }
                        }
                    }
                    is DownloadEvent.HashMismatch -> {
                        if (event.requestId == requestId) {
                            _state.update {
                                it.copy(
                                    isDownloading = false,
                                    invalidDownloadedApkFiles = event.files,
                                    invalidDownloadMessage = "Файл скачан некорректно.",
                                    invalidExpectedHash = event.expectedHash,
                                    invalidActualHash = event.actualHash
                                )
                            }
                        }
                    }
                    is DownloadEvent.FileSaved -> {
                        if (event.requestId == folderRequestId) {
                            _state.update { it.copy(isFolderDownloading = false, folderDownloadProgress = 0) }
                            Toast.makeText(
                                apkInstaller.context,
                                if (event.success) R.string.download_complete else R.string.loading_error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun handleDownloadError(throwable: Throwable?) {
        val msg = throwable?.message ?: ""
        if (msg.contains("timeout")) {
            Toast.makeText(apkInstaller.context, R.string.timeout, Toast.LENGTH_SHORT).show()
        } else if (msg.contains("Failed to allocate")) {
            Toast.makeText(apkInstaller.context, R.string.insufficient_memory, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(apkInstaller.context, R.string.loading_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun loadDetails(context: Context, itemId: String) {
        if (_state.value.item != null) {
            return
        }
        Log.d(null, "Initial loading")
        viewModelScope.launch {
            var item: ItemDetails? = null
            try {
                item = AppModule.searchRepository.getItemDetails(itemId, appPlatform)
                val packageInfo = getPackageInfo(context, itemId)
                val installer = if (packageInfo != null) getInstallSource(context.packageManager, itemId) else null

                _state.value = DetailsState(
                    isLoading = true,
                    isInstalled = packageInfo != null,
                    installerPackageName = installer
                )

                if (packageInfo != null) {
                    val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                    if (installedVersionCode < item.versionCode) {
                        _state.update { it.copy(isNeedUpgrade = true) }
                    }
                }
                _state.update { it.copy(isSupported = Build.VERSION.SDK_INT >= item.minSdkVersion) }
                _state.update { it.copy(item = item) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
            try {
                if (item == null) return@launch
                val appFiles = AppModule.searchRepository.getAppFiles(item.appId, firstInstall = true, supportedAbis = Build.SUPPORTED_ABIS.toList(), withoutSplits = false, deviceType = appPlatform)
                _state.update { it.copy(appFiles = appFiles) }
            } catch (e: Exception) {}
            try {
                if (item == null) return@launch
                val singleApk = AppModule.searchRepository.getAppFiles(item.appId, firstInstall = true, supportedAbis = Build.SUPPORTED_ABIS.toList(), withoutSplits = true, deviceType = appPlatform).firstOrNull()
                _state.update { it.copy(plainApk = singleApk, plainApkUrl = singleApk?.url, splitOnly = singleApk == null) }
            } catch (e: Exception) {}
            try {
                val rating = AppModule.searchRepository.getAppRating(itemId)
                _state.update { it.copy(rating = rating) }
            } catch (e: Exception) {}

            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun getInstallSource(pm: PackageManager, packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun downloadAndInstallApp(context: Context) {
        val packageName = _state.value.item?.packageName ?: return
        val appName = _state.value.item?.appName ?: "App"
        val files = _state.value.appFiles

        Log.d(null, "download apk $files")

        if (files == null) {
            Toast.makeText(context, R.string.loading_error, Toast.LENGTH_SHORT).show()
            return
        }

        val requestId = "install_$packageName"

        _state.update { it.copy(isDownloading = true, downloadProgress = 0, downloadTotalBytes = 0, downloadCurrentBytes = 0) }

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_REQUEST_ID, requestId)
            putExtra(DownloadService.EXTRA_APP_NAME, appName)
            putExtra(DownloadService.EXTRA_PACKAGE_NAME, packageName)
            putExtra(DownloadService.EXTRA_URLS, files.map { it.url }.toTypedArray())
            putExtra(DownloadService.EXTRA_SIZES, files.map { it.size }.toLongArray())
            putExtra(DownloadService.EXTRA_HASHES, files.map { it.hash }.toTypedArray())
            putExtra(DownloadService.EXTRA_AUTO_INSTALL, true)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelDownload() {
        val packageName = _state.value.item?.packageName ?: return
        val requestId = "install_$packageName"
        val intent = Intent(apkInstaller.context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_REQUEST_ID, requestId)
        }
        apkInstaller.context.startService(intent)
    }

    fun retryDownloadAfterInvalid(context: Context) {
        _state.update {
            it.copy(
                invalidDownloadedApkFiles = null,
                invalidDownloadMessage = null,
                invalidExpectedHash = null,
                invalidActualHash = null
            )
        }
        downloadAndInstallApp(context)
    }

    fun installAsIsAfterInvalid(context: Context) {
        val apkFiles = state.value.invalidDownloadedApkFiles ?: return
        _state.update {
            it.copy(
                invalidDownloadedApkFiles = null,
                invalidDownloadMessage = null,
                invalidExpectedHash = null,
                invalidActualHash = null
            )
        }
        val packageName = state.value.item?.packageName ?: "tmp"
        _state.update { it.copy(installStatus = InstallStatus.Pending) }
        apkInstaller.installApp(
            packageName = packageName,
            apkFiles = apkFiles,
            onSessionFinished = { success ->
                _state.update { it.copy(installStatus = InstallStatus.Success, isDownloading = false) }
                if (success) {
                    _state.update { it.copy(isInstalled = true, isNeedUpgrade = false, installerPackageName = context.packageName) }
                    apkInstaller.cleanApkFiles(apkFiles.map { it.name })
                }
            },
            onError = { error ->
                if (error.message?.contains("Failed to allocate") == true) {
                    Toast.makeText(context, R.string.insufficient_memory, Toast.LENGTH_SHORT).show()
                } else {
                    println("apk installer $error")
                    Toast.makeText(context, R.string.installing_error, Toast.LENGTH_SHORT).show()
                }
                _state.update { it.copy(installStatus = InstallStatus.Idle, isDownloading = false) }
            }
        )
    }

    fun clearInvalidDownloadState() {
        _state.update {
            it.copy(
                invalidDownloadedApkFiles = null,
                invalidDownloadMessage = null,
                invalidExpectedHash = null,
                invalidActualHash = null
            )
        }
    }

    private fun getPackageInfo(context: Context, packageName: String): PackageInfo? {
        Log.d(null, "try to check package $packageName")
        return try {
            val packageInfo: PackageInfo
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
        _state.update { it.copy(isInstalled = isInstalled, installerPackageName = if (isInstalled) it.installerPackageName else null) }
    }

    fun startFolderDownload(context: Context, destinationUri: Uri) {
        val packageName = _state.value.item?.packageName ?: return
        val appName = _state.value.item?.appName ?: "App"
        val apk = _state.value.plainApk ?: return
        if (_state.value.isFolderDownloading) return

        val requestId = "folder_$packageName"

        _state.update { it.copy(isFolderDownloading = true, folderDownloadProgress = 0) }

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD_TO_FOLDER
            putExtra(DownloadService.EXTRA_REQUEST_ID, requestId)
            putExtra(DownloadService.EXTRA_APP_NAME, appName)
            putExtra(DownloadService.EXTRA_PACKAGE_NAME, packageName)
            putExtra(DownloadService.EXTRA_URLS, arrayOf(apk.url))
            putExtra(DownloadService.EXTRA_SIZES, longArrayOf(apk.size))
            putExtra(DownloadService.EXTRA_HASHES, arrayOf(apk.hash))
            putExtra(DownloadService.EXTRA_AUTO_INSTALL, false)
            putExtra(DownloadService.EXTRA_DESTINATION_URI, destinationUri.toString())
            putExtra(DownloadService.EXTRA_VERSION_NAME, _state.value.item?.versionName ?: "unknown")
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelFolderDownload() {
        val packageName = _state.value.item?.packageName ?: return
        val requestId = "folder_$packageName"
        val intent = Intent(apkInstaller.context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_REQUEST_ID, requestId)
        }
        apkInstaller.context.startService(intent)
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
