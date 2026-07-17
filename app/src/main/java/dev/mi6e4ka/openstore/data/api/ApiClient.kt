package dev.mi6e4ka.openstore.data.api

import dev.mi6e4ka.openstore.di.AppModule
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
//import okhttp3.logging.HttpLoggingInterceptor

object ApiClient {
    private const val BASE_URL = "https://backapi.rustore.ru/"

//    private val loggingInterceptor = HttpLoggingInterceptor().apply {
//        level = HttpLoggingInterceptor.Level.NONE
//    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(AppModule.okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: ApiService by lazy { retrofit.create(ApiService::class.java) }
}