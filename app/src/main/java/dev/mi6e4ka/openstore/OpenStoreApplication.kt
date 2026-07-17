package dev.mi6e4ka.openstore

import android.app.Application
import dev.mi6e4ka.openstore.di.AppModule

class OpenStoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }
}
