package dev.mi6e4ka.openstore.data.model

import com.google.gson.annotations.SerializedName

data class AppRatingResponse(
    @SerializedName("body") val body: AppRatingResponseBody,
)

data class AppRatingResponseBody(
    @SerializedName("ratings") val ratings: AppRatingCount,
    @SerializedName("averageUserRating") val averageUserRating: Float,
    @SerializedName("totalRatings") val totalRatings: Int,
)


data class AppRatingCount(
    @SerializedName("amountFive") val amountFive: Int,
    @SerializedName("amountFour") val amountFour: Int,
    @SerializedName("amountThree") val amountThree: Int,
    @SerializedName("amountTwo") val amountTwo: Int,
    @SerializedName("amountOne") val amountOne: Int,
)