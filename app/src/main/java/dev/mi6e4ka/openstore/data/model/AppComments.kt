package dev.mi6e4ka.openstore.data.model

import com.google.gson.annotations.SerializedName

data class AppCommentsResponse(
    @SerializedName("body") val body: AppCommentsResponseBody,
)

data class AppCommentsResponseBody(
    @SerializedName("content") val content: List<AppCommentsComments>,
)

data class AppCommentsComments(
    @SerializedName("appRating") val appRating: Int,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("commentDate") val commentDate: String,
    @SerializedName("commentText") val commentText: String,
    @SerializedName("likeCounter") val likeCounter: Int,
    @SerializedName("dislikeCounter") val dislikeCounter: Int,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("devResponseDate") val devResponseDate: String,
    @SerializedName("devResponse") val devResponse: String
)