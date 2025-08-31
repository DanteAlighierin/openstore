package dev.mi6e4ka.openstore.data.model

import com.google.gson.annotations.SerializedName

data class SearchResult(
    @SerializedName("appId") val appId: Int,
    @SerializedName("appName") val appName: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("iconUrl") val iconUrl: String,
)

data class SearchResponseBody(
    @SerializedName("content") val content: List<SearchResult>
)

data class SearchResponse(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String?,
    @SerializedName("body") val body: SearchResponseBody
)