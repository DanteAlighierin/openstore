package dev.mi6e4ka.openstore.data.updater

import com.google.gson.annotations.SerializedName
import dev.mi6e4ka.openstore.BuildConfig
import dev.mi6e4ka.openstore.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CodebergRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("assets") val assets: List<CodebergReleaseAsset>?
)

data class CodebergReleaseAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long?
)

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String?,
    val releaseUrl: String,
    val downloadUrl: String?
)

data class AppUpdateResult(
    val hasUpdate: Boolean,
    val info: AppUpdateInfo? = null,
    val error: String? = null
)

object AppUpdater {

    private const val CODEBERG_API = "https://codeberg.org/api/v1/repos/mi6e4ka/openstore/releases/latest"

    suspend fun checkForUpdate(): AppUpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(CODEBERG_API)
                .header("Accept", "application/json")
                .build()

            val response = AppModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext AppUpdateResult(hasUpdate = false, error = "HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext AppUpdateResult(hasUpdate = false, error = "Empty response")

            val release = com.google.gson.Gson().fromJson(body, CodebergRelease::class.java)

            val latestTag = release.tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("git-").removePrefix("v")

            val hasUpdate = compareVersions(latestTag, currentVersion) > 0

            if (!hasUpdate) {
                return@withContext AppUpdateResult(hasUpdate = false)
            }

            val apkAsset = release.assets?.find { it.name.endsWith(".apk") }

            AppUpdateResult(
                hasUpdate = true,
                info = AppUpdateInfo(
                    latestVersion = release.tagName,
                    releaseNotes = release.body,
                    releaseUrl = release.htmlUrl,
                    downloadUrl = apkAsset?.browserDownloadUrl
                )
            )
        } catch (e: Exception) {
            AppUpdateResult(hasUpdate = false, error = e.message)
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
