package dev.mi6e4ka.openstore.ui.screen.updates

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.data.model.AppUpdateRequestEntry
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
import dev.mi6e4ka.openstore.internal.installer.DownloadEvent
import dev.mi6e4ka.openstore.internal.installer.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppWithUpdates (
    val packageName: String,
    val appName: String,
    val installedVersionCode: Long,
    val installedVersionName: String,
    val iconUrl: String? = null,
    val latestVersionCode: Long? = null,
    val latestVersionName: String? = null,
    val appId: Int? = null,
    val installSource: String
)

data class UpdatesState(
    val isLoading: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val installedApps: List<AppUpdateRequestEntry> = emptyList(),
    val appsWithUpdates: List<AppWithUpdates> = emptyList(),
    val error: String? = null,
    val downloadingPackages: Set<String> = emptySet(),
    val downloadProgress: Map<String, Int> = emptyMap(),
    val invalidDownloadedApkFiles: List<File>? = null,
    val invalidDownloadedAppPackage: String? = null,
    val invalidExpectedHash: String? = null,
    val invalidActualHash: String? = null
)

class UpdatesViewModel(private val apkInstaller: ApkInstaller) : ViewModel() {
    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    private val downloadQueue = mutableListOf<AppWithUpdates>()
    private var currentRequestId: String? = null

    companion object {
        private const val DEFAULT_SOURCES = "dev.mi6e4ka.openstore;dev.mi6e4ka.openstore.nightly;dev.mi6e4ka.openstore.debug;ru.vk.store"
    }

    init {
        viewModelScope.launch {
            AppModule.downloadEventFlow.events.collect { event ->
                if (currentRequestId == null || event.requestId != currentRequestId) return@collect

                when (event) {
                    is DownloadEvent.Progress -> {
                        val pkg = event.requestId.removePrefix("update_")
                        _state.update {
                            it.copy(
                                downloadProgress = it.downloadProgress + (pkg to if (event.percent == 100) 101 else event.percent)
                            )
                        }
                    }
                    is DownloadEvent.Success -> {
                        val pkg = event.requestId.removePrefix("update_")
                        _state.update {
                            it.copy(
                                downloadingPackages = it.downloadingPackages - pkg,
                                downloadProgress = it.downloadProgress - pkg,
                                appsWithUpdates = it.appsWithUpdates.filter { app -> app.packageName != pkg }
                            )
                        }
                        currentRequestId = null
                        processQueue(apkInstaller.context)
                    }
                    is DownloadEvent.Error -> {
                        val pkg = event.requestId.removePrefix("update_")
                        _state.update {
                            it.copy(
                                downloadingPackages = it.downloadingPackages - pkg,
                                downloadProgress = it.downloadProgress - pkg
                            )
                        }
                        currentRequestId = null
                        processQueue(apkInstaller.context)
                    }
                    is DownloadEvent.Canceled -> {
                        val pkg = event.requestId.removePrefix("update_")
                        _state.update {
                            it.copy(
                                downloadingPackages = it.downloadingPackages - pkg,
                                downloadProgress = it.downloadProgress - pkg
                            )
                        }
                        currentRequestId = null
                        processQueue(apkInstaller.context)
                    }
                    is DownloadEvent.HashMismatch -> {
                        val pkg = event.requestId.removePrefix("update_")
                        _state.update {
                            it.copy(
                                downloadingPackages = it.downloadingPackages - pkg,
                                downloadProgress = it.downloadProgress - pkg,
                                invalidDownloadedApkFiles = event.files,
                                invalidDownloadedAppPackage = pkg,
                                invalidExpectedHash = event.expectedHash,
                                invalidActualHash = event.actualHash
                            )
                        }
                        currentRequestId = null
                        processQueue(apkInstaller.context)
                    }
                }
            }
        }
    }

    fun loadAndCheckUpdates(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val installedApps = try {
                withContext(Dispatchers.IO) {
                    getInstalledApps(context)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
                return@launch
            }
            _state.update { it.copy(installedApps = installedApps, isLoading = false) }

            checkForUpdatesInternal(installedApps)
        }
    }

    fun checkForUpdates(context: Context) {
        loadAndCheckUpdates(context)
    }

    private suspend fun checkForUpdatesInternal(installedApps: List<AppUpdateRequestEntry>) {
        _state.update { it.copy(isCheckingUpdates = true, appsWithUpdates = emptyList()) }

        val rawUpdates = AppModule.searchRepository.getBatchUpdates(installedApps)

        if (rawUpdates.isEmpty()) {
            _state.update {
                it.copy(
                    isCheckingUpdates = false,
                    appsWithUpdates = emptyList()
                )
            }
            return
        }

        val detailsUpdates = AppModule.searchRepository.getBatchItemDetails(rawUpdates.map { it.packageName })

        val detailsByPackageName = detailsUpdates.associateBy { it.packageName }
        val installedAppsByPackageName = installedApps.associateBy { it.packageName }

        val appsWithUpdates = rawUpdates.mapNotNull {
            val details = detailsByPackageName[it.packageName] ?: return@mapNotNull null
            val installedApp = installedAppsByPackageName[it.packageName] ?: return@mapNotNull null
            AppWithUpdates(
                packageName = it.packageName,
                appName = it.appName,
                installedVersionCode = installedApp.versionCode,
                installedVersionName = installedApp.versionName,
                iconUrl = details.iconUrl,
                latestVersionCode = details.versionCode,
                latestVersionName = "?",
                appId = it.appId,
                installSource = installedApp.installSource
            )
        }

        _state.update {
            it.copy(
                isCheckingUpdates = false,
                appsWithUpdates = appsWithUpdates
            )
        }
    }

    fun downloadAndUpdateApp(context: Context, app: AppWithUpdates) {
        downloadQueue.add(app)
        processQueue(context)
    }

    private fun processQueue(context: Context) {
        if (currentRequestId != null || downloadQueue.isEmpty()) return

        val app = downloadQueue.removeAt(0)
        if (app.appId == null) {
            processQueue(context)
            return
        }

        val requestId = "update_${app.packageName}"

        viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingPackages = it.downloadingPackages + app.packageName,
                    downloadProgress = it.downloadProgress + (app.packageName to 0)
                )
            }

            try {
                val appFiles = withContext(Dispatchers.IO) {
                    AppModule.searchRepository.getAppFiles(
                        app.appId,
                        firstInstall = false,
                        supportedAbis = Build.SUPPORTED_ABIS.toList(),
                        withoutSplits = false,
                        deviceType = "mobile"
                    )
                }

                currentRequestId = requestId

                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START_DOWNLOAD
                    putExtra(DownloadService.EXTRA_REQUEST_ID, requestId)
                    putExtra(DownloadService.EXTRA_APP_NAME, app.appName)
                    putExtra(DownloadService.EXTRA_PACKAGE_NAME, app.packageName)
                    putExtra(DownloadService.EXTRA_URLS, appFiles.map { it.url }.toTypedArray())
                    putExtra(DownloadService.EXTRA_SIZES, appFiles.map { it.size }.toLongArray())
                    putExtra(DownloadService.EXTRA_HASHES, appFiles.map { it.hash }.toTypedArray())
                    putExtra(DownloadService.EXTRA_AUTO_INSTALL, true)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                currentRequestId = null
                _state.update {
                    it.copy(
                        downloadingPackages = it.downloadingPackages - app.packageName,
                        downloadProgress = it.downloadProgress - app.packageName
                    )
                }
                processQueue(context)
            }
        }
    }

    fun cancelDownload(packageName: String) {
        if (currentRequestId == "update_$packageName") {
            val intent = Intent(apkInstaller.context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL_DOWNLOAD
                putExtra(DownloadService.EXTRA_REQUEST_ID, currentRequestId)
            }
            apkInstaller.context.startService(intent)
        }
        downloadQueue.removeAll { it.packageName == packageName }
    }

    fun retryDownloadAfterInvalid(context: Context) {
        val packageName = state.value.invalidDownloadedAppPackage ?: return
        _state.update {
            it.copy(
                invalidDownloadedAppPackage = null,
                invalidDownloadedApkFiles = null,
                invalidExpectedHash = null,
                invalidActualHash = null
            )
        }
        val app = state.value.appsWithUpdates.find { it.packageName == packageName } ?: return
        downloadAndUpdateApp(context, app)
    }

    fun installAsIsAfterInvalid(context: Context) {
        val packageName = state.value.invalidDownloadedAppPackage ?: return
        val apkFiles = state.value.invalidDownloadedApkFiles ?: return
        _state.update {
            it.copy(
                invalidDownloadedAppPackage = null,
                invalidDownloadedApkFiles = null,
                invalidExpectedHash = null,
                invalidActualHash = null
            )
        }
        installApp(context, packageName, apkFiles)
    }

    fun clearInvalidDownloadState() {
        _state.update {
            it.copy(
                invalidDownloadedAppPackage = null,
                invalidDownloadedApkFiles = null,
                invalidExpectedHash = null,
                invalidActualHash = null
            )
        }
    }

    private fun installApp(context: Context, packageName: String, apkFiles: List<File>) {
        apkInstaller.installApp(
            packageName = packageName,
            apkFiles = apkFiles,
            onSessionFinished = { success ->
                _state.update {
                    it.copy(
                        downloadingPackages = it.downloadingPackages - packageName,
                        downloadProgress = it.downloadProgress - packageName,
                        appsWithUpdates = if (success) {
                            it.appsWithUpdates.filter { app -> app.packageName != packageName }
                        } else it.appsWithUpdates
                    )
                }
                if (success) {
                    apkInstaller.cleanApkFiles(apkFiles.map { it.name })
                }
            },
            onError = { error ->
                handleError(context, packageName, error)
            }
        )
    }

    private fun handleError(context: Context, packageName: String, error: Throwable) {
        if (error.message?.contains("Failed to allocate") == true) {
            Toast.makeText(context, R.string.insufficient_memory, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.loading_error, Toast.LENGTH_SHORT).show()
        }
        _state.update {
            it.copy(
                downloadingPackages = it.downloadingPackages - packageName,
                downloadProgress = it.downloadProgress - packageName
            )
        }
    }

    private fun getInstalledApps(context: Context): List<AppUpdateRequestEntry> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        val allowedSources = prefs.getString("updates_sources", DEFAULT_SOURCES)?.split(";") ?: listOf()
        val allowAllSources = prefs.getBoolean("updates_check_all", false)

        return packages
            .filter { !isSystemApp(it) && (allowAllSources || allowedSources.contains(getInstallSource(pm, it.packageName))) }
            .map { packageInfo ->
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                println("package " + packageInfo.packageName + " from " + getInstallSource(pm, packageInfo.packageName))
                AppUpdateRequestEntry(
                    packageName = packageInfo.packageName,
                    appName = pm.getApplicationLabel(packageInfo.applicationInfo!!).toString(),
                    versionCode = versionCode,
                    versionName = packageInfo.versionName ?: "Unknown",
                    installSource = getInstallSource(pm, packageInfo.packageName)
                )
            }
    }

    private fun isSystemApp(packageInfo: PackageInfo): Boolean {
        return (packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
    }

    private fun getInstallSource(pm: PackageManager, packageName: String): String {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } catch (e: Exception) {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
        return installer ?: "null"
    }
}
