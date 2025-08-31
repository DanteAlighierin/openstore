package dev.mi6e4ka.openstore.data.repository

import dev.mi6e4ka.openstore.data.api.ApiClient
import dev.mi6e4ka.openstore.data.model.AppCommentsComments
import dev.mi6e4ka.openstore.data.model.AppRatingResponseBody
import dev.mi6e4ka.openstore.data.model.DownloadLinkRequest
import dev.mi6e4ka.openstore.data.model.ItemDetails
import dev.mi6e4ka.openstore.data.model.SearchResult

class SearchRepository {
    private val apiService = ApiClient.service

    suspend fun search(query: String): List<SearchResult> {
        return apiService.search(20, query).body.content
    }

    suspend fun getItemDetails(id: String): ItemDetails {
        return apiService.getItemDetails(id).body
    }

    suspend fun getAppDownloadLink(id: Int): String {
        val req = DownloadLinkRequest(appId = id, firstInstall = true, screenDensity = 420, sdkVersion = 36, withoutSplits = true)
        return apiService.getAppDownloadLink(req).body.downloadUrls[0].url
    }

    suspend fun getAppRating(packageName: String): AppRatingResponseBody {
        return apiService.getAppRating(packageName).body
    }

    suspend fun getAppComments(packageName: String, pageSize: Int, sortBy: String): List<AppCommentsComments> {
        return apiService.getAppComments(packageName, pageSize = pageSize, sortBy = sortBy).body.content
    }
}