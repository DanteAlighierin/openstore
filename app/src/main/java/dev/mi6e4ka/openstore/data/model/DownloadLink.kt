package dev.mi6e4ka.openstore.data.model

import com.google.gson.annotations.SerializedName

data class DownloadLinkRequest(
    @SerializedName("appId") val appId: Int,
    @SerializedName("firstInstall") val firstInstall: Boolean,
    @SerializedName("screenDensity") val screenDensity: Int,
    @SerializedName("sdkVersion") val sdkVersion: Int,
    @SerializedName("withoutSplits") val withoutSplits: Boolean,
    @SerializedName("supportedAbis") val supportedAbis: List<String>,
)

data class DownloadLinkResponse(
    @SerializedName("body") val body: DownloadLinkResponseBody,
)

data class DownloadLinkResponseBody(
    @SerializedName("downloadUrls") val downloadUrls: List<DownloadLinkResponseItem>,
)

data class DownloadLinkResponseItem(
    @SerializedName("url") val url: String,
    @SerializedName("size") val size: Long,
    @SerializedName("hash") val hash: String,
)