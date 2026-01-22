package dev.mi6e4ka.openstore.ui.screen.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller

class UpdatesViewModelFactory(
    private val apkInstaller: ApkInstaller
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpdatesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UpdatesViewModel(apkInstaller) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
