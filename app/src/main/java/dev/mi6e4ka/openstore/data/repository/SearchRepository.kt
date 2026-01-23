package dev.mi6e4ka.openstore.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import dev.mi6e4ka.openstore.data.api.ApiClient
import dev.mi6e4ka.openstore.data.model.AppCommentsComments
import dev.mi6e4ka.openstore.data.model.AppRatingResponseBody
import dev.mi6e4ka.openstore.data.model.AppUpdateRequest
import dev.mi6e4ka.openstore.data.model.AppUpdateRequestEntry
import dev.mi6e4ka.openstore.data.model.AppUpdateResponse
import dev.mi6e4ka.openstore.data.model.AppUpdateResponseEntry
import dev.mi6e4ka.openstore.data.model.BatchItemDetailsRequest
import dev.mi6e4ka.openstore.data.model.DownloadLinkRequest
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponseItem
import dev.mi6e4ka.openstore.data.model.ItemDetails
import dev.mi6e4ka.openstore.data.model.SearchResult
import dev.mi6e4ka.openstore.data.model.ShortItemDetails
import dev.mi6e4ka.openstore.data.paging.SearchPagingSource

class SearchRepository {
    private val apiService = ApiClient.service

    fun search(query: String, deviceType : String): Pager<Int, SearchResult> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { SearchPagingSource(apiService, query, deviceType) }
        )
    }

    suspend fun getItemDetails(packageName: String, deviceType: String): ItemDetails {
        return apiService.getItemDetails(deviceType, packageName).body
    }

    suspend fun getAppFiles(id: Int, firstInstall: Boolean, supportedAbis: List<String>, withoutSplits: Boolean, deviceType: String): List<DownloadLinkResponseItem> {
        val req = DownloadLinkRequest(
            appId = id,
            firstInstall = firstInstall,
            screenDensity = 420,
            sdkVersion = 36,
            withoutSplits = withoutSplits,
            supportedAbis = supportedAbis
        )
        return  apiService.getAppDownloadLink(deviceType, req).body.downloadUrls
    }

    suspend fun getAppRating(packageName: String): AppRatingResponseBody {
        return apiService.getAppRating(packageName).body
    }

    suspend fun getAppComments(packageName: String, pageSize: Int, sortBy: String): List<AppCommentsComments> {
        return apiService.getAppComments(packageName, pageSize = pageSize, sortBy = sortBy).body.content
    }

    suspend fun getBatchUpdates(apps: List<AppUpdateRequestEntry>): List<AppUpdateResponseEntry> {
        return apiService.getBatchUpdates(AppUpdateRequest(content = apps)).body.content
    }

    suspend fun getBatchItemDetails(packageNames: List<String>) : List<ShortItemDetails> {
        return apiService.getBatchItemDetails(request = BatchItemDetailsRequest(packageNames = packageNames))
    }
}