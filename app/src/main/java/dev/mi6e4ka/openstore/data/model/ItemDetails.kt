package dev.mi6e4ka.openstore.data.model

import com.google.gson.annotations.SerializedName

data class ItemDetails(
    @SerializedName("appId") val appId: Int,
    @SerializedName("appName") val appName: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("companyName") val companyName: String,
    @SerializedName("iconUrl") val iconUrl: String,
    @SerializedName("fullDescription") val fullDescription: String,
    @SerializedName("shortDescription") val shortDescription: String,
    @SerializedName("fileSize") val fileSize: Float,
    @SerializedName("minSdkVersion") val minSdkVersion: Int,
    @SerializedName("targetSdkVersion") val targetSdkVersion: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Long,
    @SerializedName("fileUrls") val fileUrls: List<Files>,
    @SerializedName("aggregatorInfo") val aggregatorInfo: AggregatorInfo?,
    @SerializedName("roundedDownloadsText") val roundedDownloadsText: String,
    @SerializedName("ageLegal") val ageLegal: String,
    @SerializedName("whatsNew") val whatsNew: String,
)

data class Files(
    @SerializedName("fileUrl") val fileUrl: String,
    @SerializedName("ordinal") val ordinal: Int,
    @SerializedName("type") val type: String,
    @SerializedName("orientation") val orientation: String,
)

data class AggregatorInfo(
    @SerializedName("companyName") val companyName: String,
    @SerializedName("source") val source: String,
)

data class ItemDetailsResponse(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String?,
    @SerializedName("body") val body: ItemDetails,
)