package dev.mi6e4ka.openstore.di

import dev.mi6e4ka.openstore.data.repository.SearchRepository
import dev.mi6e4ka.openstore.internal.installer.UninstallerEventFlow

object AppModule {
    val searchRepository: SearchRepository by lazy {
        SearchRepository()
    }

    val uninstallerEventFlow: UninstallerEventFlow by lazy {
        UninstallerEventFlow()
    }
}