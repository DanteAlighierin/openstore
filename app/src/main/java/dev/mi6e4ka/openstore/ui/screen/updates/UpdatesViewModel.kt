package dev.mi6e4ka.openstore.ui.screen.updates

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mi6e4ka.openstore.data.model.AppUpdateRequestEntry
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
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
    val excludeGooglePlay: Boolean = true
)

class UpdatesViewModel(private val apkInstaller: ApkInstaller) : ViewModel() {
    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()
    
    private val cancelDownloads = mutableMapOf<String, () -> Unit>()
    
    companion object {
        private const val PREFS_NAME = "updates_prefs"
        private const val KEY_EXCLUDE_GOOGLE_PLAY = "exclude_google_play"
    }
    
    fun initPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val excludeGooglePlay = prefs.getBoolean(KEY_EXCLUDE_GOOGLE_PLAY, true)
        _state.update { it.copy(excludeGooglePlay = excludeGooglePlay) }
    }

    fun toggleExcludeGooglePlay(context: Context) {
        val newValue = !_state.value.excludeGooglePlay
        _state.update { it.copy(excludeGooglePlay = newValue) }
        
        // Сохраняем в SharedPreferences
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXCLUDE_GOOGLE_PLAY, newValue)
            .apply()

        loadAndCheckUpdates(context)
    }

    fun loadAndCheckUpdates(context: Context) {
        viewModelScope.launch {
            // Загружаем список приложений
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
            
            // Сразу запускаем проверку
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

        val detailsUpdates = AppModule.searchRepository.getBatchItemDetails(rawUpdates.map {it.packageName} )

        val detailsByPackageName = detailsUpdates.associateBy {it.packageName}
        val installedAppsByPackageName = installedApps.associateBy {it.packageName}

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
        if (app.appId == null) return
        
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
                
                cancelDownloads[app.packageName] = apkInstaller.downloadFilesWithProgress(
                    appFiles,
                    onProgress = { percent, _, _ ->
                        _state.update { 
                            it.copy(downloadProgress = it.downloadProgress + (app.packageName to percent))
                        }
                    },
                    onSuccess = { apkFiles ->
                        installApp(context, app.packageName, apkFiles)
                    },
                    onError = { _ ->
                        _state.update { 
                            it.copy(
                                downloadingPackages = it.downloadingPackages - app.packageName,
                                downloadProgress = it.downloadProgress - app.packageName
                            )
                        }
                    },
                    onCancel = {
                        _state.update { 
                            it.copy(
                                downloadingPackages = it.downloadingPackages - app.packageName,
                                downloadProgress = it.downloadProgress - app.packageName
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        downloadingPackages = it.downloadingPackages - app.packageName,
                        downloadProgress = it.downloadProgress - app.packageName
                    )
                }
            }
        }
    }

    private fun installApp(context: Context, packageName: String, apkFiles: List<File>) {
        viewModelScope.launch {
            val installerCallback = object : PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) {}
                override fun onBadgingChanged(sessionId: Int) {}
                override fun onActiveChanged(sessionId: Int, active: Boolean) {}
                override fun onProgressChanged(sessionId: Int, progress: Float) {}
                override fun onFinished(sessionId: Int, success: Boolean) {
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
                }
            }
            
            apkInstaller.installSplitApk(context, apkFiles, installerCallback, onError = { _ ->
                _state.update { 
                    it.copy(
                        downloadingPackages = it.downloadingPackages - packageName,
                        downloadProgress = it.downloadProgress - packageName
                    )
                }
            })
        }
    }

    fun cancelDownload(packageName: String) {
        cancelDownloads[packageName]?.invoke()
        cancelDownloads.remove(packageName)
    }

    private fun getInstalledApps(context: Context): List<AppUpdateRequestEntry> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        
        val excludeGooglePlay = _state.value.excludeGooglePlay
        
        return packages
            .filter { !isSystemApp(it) && !(excludeGooglePlay && getInstallSource(pm, it.packageName) == "com.android.vending") }
            .map { packageInfo ->
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

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
