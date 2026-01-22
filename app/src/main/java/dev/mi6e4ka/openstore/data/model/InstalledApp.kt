package dev.mi6e4ka.openstore.data.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val installedVersionCode: Long,
    val installedVersionName: String,
    val iconUrl: String? = null,
    val latestVersionCode: Long? = null,
    val latestVersionName: String? = null,
    val hasUpdate: Boolean = false,
    val appId: Int? = null,
    val fileSize: Float? = null
)
