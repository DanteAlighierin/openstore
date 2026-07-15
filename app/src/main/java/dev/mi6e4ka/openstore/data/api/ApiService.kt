package dev.mi6e4ka.openstore.data.api

import dev.mi6e4ka.openstore.data.model.AppCommentsResponse
import dev.mi6e4ka.openstore.data.model.AppRatingResponse
import dev.mi6e4ka.openstore.data.model.AppUpdateRequest
import dev.mi6e4ka.openstore.data.model.AppUpdateResponse
import dev.mi6e4ka.openstore.data.model.BatchItemDetailsRequest
import dev.mi6e4ka.openstore.data.model.DownloadLinkRequest
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponse
import dev.mi6e4ka.openstore.data.model.ItemDetailsResponse
import dev.mi6e4ka.openstore.data.model.SearchResponse
import dev.mi6e4ka.openstore.data.model.ShortItemDetails
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("applicationData/apps")
    suspend fun search(
        @Query("pageSize") pageSize: Int,
        @Query("pageNumber") pageNumber: Int,
        @Query("query") query: String,
        @Query("buyeruid") buyeruid : String,
        @Header("deviceType") deviceType : String
    ): SearchResponse

    @GET("applicationData/overallInfo/{packageName}")
    suspend fun getItemDetails(
        @Header("deviceType") deviceType : String,
        @Path("packageName") packageName: String

    ): ItemDetailsResponse

    @POST("v3/showcase/apps/download-link")
    suspend fun getAppDownloadLink(
        @Header("deviceType") deviceType : String,
        @Body request: DownloadLinkRequest
    ) : DownloadLinkResponse

    @GET("comment/findRating")
    suspend fun getAppRating(@Query("packageName") packageName: String) : AppRatingResponse

    @GET("https://backapi.rustore.ru/comment/comment")
    suspend fun getAppComments(
        @Query("packageName") packageName: String,
        @Query("pageNumber") pageNumber: Int = 0,
        @Query("pageSize") pageSize: Int,
        @Query("sortBy") sortBy: String,
    ) : AppCommentsResponse

    @POST("applicationData/newApps")
    suspend fun getBatchUpdates(
        @Body request: AppUpdateRequest
    ) : AppUpdateResponse

    @POST("v2/showcase/store-app")
    suspend fun getBatchItemDetails(
        @Body request: BatchItemDetailsRequest
    ) : List<ShortItemDetails>
}