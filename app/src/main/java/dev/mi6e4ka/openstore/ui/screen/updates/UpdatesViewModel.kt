package dev.mi6e4ka.openstore.ui.screen.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mi6e4ka.openstore.data.model.InstalledApp
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class UpdatesState(
    val isLoading: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val installedApps: List<InstalledApp> = emptyList(),
    val appsWithUpdates: List<InstalledApp> = emptyList(),
    val error: String? = null,
    val checkedCount: Int = 0,
    val totalToCheck: Int = 0,
    val downloadingPackages: Set<String> = emptySet(),
    val downloadProgress: Map<String, Int> = emptyMap(),
    val excludeGooglePlay: Boolean = true
)

class UpdatesViewModel(private val apkInstaller: ApkInstaller) : ViewModel() {
    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()
    
    private val cancelDownloads = mutableMapOf<String, () -> Unit>()
    private var hasStartedCheck = false
    
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
        
        hasStartedCheck = false
        loadAndCheckUpdates(context)
    }

    fun loadAndCheckUpdates(context: Context) {
        if (hasStartedCheck) return
        hasStartedCheck = true
        
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
            checkForUpdatesInternal(context, installedApps)
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val installedApps = withContext(Dispatchers.IO) {
                    getInstalledApps(context)
                }
                _state.update { it.copy(installedApps = installedApps, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun checkForUpdates(context: Context) {
        viewModelScope.launch {
            val installedApps = _state.value.installedApps.ifEmpty {
                withContext(Dispatchers.IO) { getInstalledApps(context) }
            }
            checkForUpdatesInternal(context, installedApps)
        }
    }
    
    private suspend fun checkForUpdatesInternal(context: Context, installedApps: List<InstalledApp>) {
        _state.update { it.copy(isCheckingUpdates = true, checkedCount = 0, appsWithUpdates = emptyList(), totalToCheck = installedApps.size) }
        
        val semaphore = Semaphore(5) // Уменьшил до 5 параллельных запросов
        val checkedCounter = AtomicInteger(0)
        
        val results = withContext(Dispatchers.IO) {
            installedApps.map { app ->
                async {
                    semaphore.withPermit {
                        val result = fetchDetailsWithRetry(app, maxRetries = 3)
                        val count = checkedCounter.incrementAndGet()
                        _state.update { it.copy(checkedCount = count) }
                        result
                    }
                }
            }.awaitAll()
        }
        
        val appsWithUpdates = results.filterNotNull()
        
        _state.update { 
            it.copy(
                isCheckingUpdates = false, 
                appsWithUpdates = appsWithUpdates
            ) 
        }
    }
    
    private suspend fun fetchDetailsWithRetry(app: InstalledApp, maxRetries: Int): InstalledApp? {
        repeat(maxRetries) { attempt ->
            try {
                val details = AppModule.searchRepository.getItemDetails(app.packageName, "mobile")
                if (details.versionCode > app.installedVersionCode) {
                    return app.copy(
                        iconUrl = details.iconUrl,
                        latestVersionCode = details.versionCode,
                        latestVersionName = details.versionName,
                        hasUpdate = true,
                        appId = details.appId,
                        fileSize = details.fileSize
                    )
                }
                return null // Приложение найдено, но обновления нет
            } catch (e: Exception) {
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // Увеличил задержку: 1с, 2с, 3с
                }
            }
        }
        return null
    }

    fun downloadAndUpdateApp(context: Context, app: InstalledApp) {
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

    private fun installApp(context: Context, packageName: String, apkFiles: List<java.io.File>) {
        viewModelScope.launch {
            val installerCallback = object : android.content.pm.PackageInstaller.SessionCallback() {
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

    private fun getInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        
        val excludeGooglePlay = _state.value.excludeGooglePlay
        
        return packages
            .filter { !isSystemApp(it) && (!excludeGooglePlay || !isFromGooglePlay(pm, it.packageName)) }
            .map { packageInfo ->
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                
                InstalledApp(
                    packageName = packageInfo.packageName,
                    appName = pm.getApplicationLabel(packageInfo.applicationInfo!!).toString(),
                    installedVersionCode = versionCode,
                    installedVersionName = packageInfo.versionName ?: "Unknown"
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    private fun isSystemApp(packageInfo: PackageInfo): Boolean {
        return (packageInfo.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
    }
    
    private fun isFromGooglePlay(pm: PackageManager, packageName: String): Boolean {
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
        return installer == "com.android.vending"
    }
}
