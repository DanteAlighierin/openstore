package dev.mi6e4ka.openstore.di

import android.annotation.SuppressLint
import android.content.Context
import dev.mi6e4ka.openstore.data.api.NetworkConfig
import dev.mi6e4ka.openstore.data.repository.SearchRepository
import dev.mi6e4ka.openstore.internal.installer.DownloadEventFlow
import dev.mi6e4ka.openstore.internal.installer.UninstallerEventFlow
import okhttp3.OkHttpClient

@SuppressLint("StaticFieldLeak")
object AppModule {
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        NetworkConfig.provideOkHttpClient(appContext)
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository()
    }

    val uninstallerEventFlow: UninstallerEventFlow by lazy {
        UninstallerEventFlow()
    }

    val downloadEventFlow: DownloadEventFlow by lazy {
        DownloadEventFlow()
    }
}