package dev.mi6e4ka.openstore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.net.toUri

class EditPreferencesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MainActivity::class.java).apply {
            data = "openstore://settings".toUri()
        }
        startActivity(intent)
        finish()
    }
}