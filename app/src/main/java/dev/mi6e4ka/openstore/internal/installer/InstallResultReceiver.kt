package dev.mi6e4ka.openstore.internal.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import androidx.core.os.BundleCompat
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.di.AppModule

class PackageInstallerStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val isUninstall = intent.getBooleanExtra(ACTION_UNINSTALL, false)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, if (isUninstall) R.string.app_uninstalled else R.string.app_installed, Toast.LENGTH_SHORT).show()
                if (isUninstall) {
                    AppModule.uninstallerEventFlow.trigger()
                }
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val promptIntent = BundleCompat.getParcelable(intent.extras!!, Intent.EXTRA_INTENT, Intent::class.java)

                promptIntent?.let {
                    it.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    it.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending")
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    context.startActivity(it)
                }
            }
            else -> {
                val errorMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    // User canceled the installation, do nothing
                } else if (errorMessage?.contains("INSUFFICIENT_STORAGE", ignoreCase = true) == true) {
                    Toast.makeText(context, R.string.insufficient_memory, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (isUninstall) R.string.failed_uninstall else R.string.failed_install, Toast.LENGTH_SHORT).show()
                }

                Log.e("app manager", errorMessage ?: "no error message")
            }
        }
    }

    companion object {
        const val ACTION_UNINSTALL = "action_uninstall"
    }
}
