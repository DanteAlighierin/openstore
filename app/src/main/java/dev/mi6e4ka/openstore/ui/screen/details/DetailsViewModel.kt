package dev.mi6e4ka.openstore.ui.screen.details

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mi6e4ka.openstore.data.model.AppCommentsResponseBody
import dev.mi6e4ka.openstore.data.model.AppRatingResponseBody
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

data class DetailsState(
    val isLoading: Boolean = true,
    val item: ItemDetails? = null,
    val rating: AppRatingResponseBody? = null,
    val error: String? = null,
    val downloadLink: String? = null,
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

class DetailsViewModel(private val itemId: String, private val apkInstaller: ApkInstaller) : ViewModel() {
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()
    private var cancelDownload: (() -> Unit)? = null

    fun loadDetails(context: Context, itemId: String) {
        if (_state.value.item != null) {
            return
        }
        Log.d(null, "Initial loading")
        viewModelScope.launch {
            try {
                val item = AppModule.searchRepository.getItemDetails(itemId)
                //
                val packageInfo  = getPackageInfo(context, itemId)
                _state.value = DetailsState(isLoading = true, isInstalled = packageInfo != null)
                if (packageInfo != null) {
                    val installedVersionCode = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        packageInfo.versionCode
                    }) as Long
                    if (installedVersionCode < item.versionCode) {
                        _state.update { it.copy(isNeedUpgrade = true) }
                    }
                }
                //
                _state.update { it.copy(isSupported = Build.VERSION.SDK_INT >= item.minSdkVersion) }

                val downloadLink = AppModule.searchRepository.getAppDownloadLink(item.appId)
                val rating = AppModule.searchRepository.getAppRating(itemId)
                _state.update{ it.copy(item = item, rating = rating, downloadLink = downloadLink, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun downloadAndInstallApk(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true, downloadProgress = 0, downloadTotalBytes = 0, downloadCurrentBytes = 0) }
            Log.d(null, "download apk " + state.value.downloadLink)
            cancelDownload = apkInstaller.downloadApkWithProgress(
                state.value.downloadLink ?: "",
                onProgress = { percent, downloaded, total ->
                    _state.update { it.copy(downloadProgress = percent, downloadCurrentBytes = downloaded, downloadTotalBytes = total) }
                },
                onSuccess = { apkFile ->
                    installApk(context, apkFile)
                },
                onError = { error ->
                    if (error.message?.contains("Canceled") == true || error.message?.contains("Socket is closed") == true || error.message?.contains("CANCEL") == true) {
                        return@downloadApkWithProgress
                    } else if (error.message?.contains("timeout") == true) {
                        Toast.makeText(context, "Превышено время ожидания", Toast.LENGTH_SHORT).show()
                    } else if (error.message?.contains("Failed to allocate") == true) {
                        Toast.makeText(context, "Недостаточно памяти на устройстве", Toast.LENGTH_SHORT).show()
                    } else {
                        println("ERROR: ${error.message}")
                        Toast.makeText(context, "Ошбика загрузки", Toast.LENGTH_SHORT).show()
                    }
                    _state.update { it.copy(isDownloading = false) }
                },
                onCancel = {
                    _state.update { it.copy(isDownloading = false) }
                }
            )
//                                        val uri = FileProvider.getUriForFile(
//                                            context,
//                                            "${context.packageName}.provider",
//                                            apkFile
//                                        )
//
//                                        val intent = Intent(Intent.ACTION_VIEW).apply {
//                                            setDataAndType(uri, "application/vnd.android.package-archive")
//                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                                        }
//                                        launcher.launch(intent)
        }
    }

    fun cancelDownload() {
        cancelDownload?.invoke()
        cancelDownload = null
    }

    private fun installApk(context: Context, apkFile: File) {
        viewModelScope.launch {
            _state.update { it.copy(installStatus = InstallStatus.Pending) }
            val installerCallback = object : android.content.pm.PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) {}
                override fun onBadgingChanged(sessionId: Int) {}
                override fun onActiveChanged(sessionId: Int, active: Boolean) {}
                override fun onProgressChanged(sessionId: Int, progress: Float) {}
                override fun onFinished(sessionId: Int, success: Boolean) {
                    _state.update { it.copy(installStatus = InstallStatus.Success, isDownloading = false)}
                    if (success) {
                        _state.update { it.copy(isInstalled = true, isNeedUpgrade = false) }
                        apkInstaller.deleteApkFile(apkFile.name)
                    }
                }
            }
            apkInstaller.installApk(context, apkFile, installerCallback, onError = { error ->
                if (error.message?.contains("Failed to allocate") == true) {
                    Toast.makeText(context, "Недостаточно памяти на устройстве", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Ошибка во время установки", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
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
