package dev.mi6e4ka.openstore.data.api

import dev.mi6e4ka.openstore.data.model.AppCommentsResponse
import dev.mi6e4ka.openstore.data.model.AppRatingResponse
import dev.mi6e4ka.openstore.data.model.DownloadLinkRequest
import dev.mi6e4ka.openstore.data.model.DownloadLinkResponse
import dev.mi6e4ka.openstore.data.model.ItemDetailsResponse
import dev.mi6e4ka.openstore.data.model.SearchResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("applicationData/apps")
    suspend fun search(@Query("pageSize") pageSize: Int, @Query("query") query: String): SearchResponse

    @GET("applicationData/overallInfo/{packageName}")
    suspend fun getItemDetails(@Path("packageName") packageName: String): ItemDetailsResponse

    @POST("applicationData/v2/download-link")
    suspend fun getAppDownloadLink(@Body request: DownloadLinkRequest) : DownloadLinkResponse

    @GET("comment/findRating")
    suspend fun getAppRating(@Query("packageName") packageName: String) : AppRatingResponse

    @GET("https://backapi.rustore.ru/comment/comment")
    suspend fun getAppComments(
        @Query("packageName") packageName: String,
        @Query("pageNumber") pageNumber: Int = 0,
        @Query("pageSize") pageSize: Int,
        @Query("sortBy") sortBy: String,
    ) : AppCommentsResponse
}