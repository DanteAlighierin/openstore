package dev.mi6e4ka.openstore.data.model

import com.google.gson.annotations.SerializedName

data class AppUpdateRequestEntry(
    @SerializedName("packageName") val packageName: String,
    @Transient val appName: String,
    @SerializedName("versionCode") val versionCode: Long,
    @Transient val versionName: String,
    @Transient val installSource: String
)

data class AppUpdateRequest(
    @SerializedName("content") val content: List<AppUpdateRequestEntry>
)

data class AppUpdateResponse(
    @SerializedName("body") val body: AppUpdateResponseBody
)

data class AppUpdateResponseBody(
    @SerializedName("content") val content: List<AppUpdateResponseEntry>
)

data class AppUpdateResponseEntry(
    @SerializedName("appId") val appId: Int,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("versionCode") val versionCode: Long
)