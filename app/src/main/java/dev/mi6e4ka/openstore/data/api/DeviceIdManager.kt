package dev.mi6e4ka.openstore.data.api

import android.content.Context
import java.util.Random
import androidx.core.content.edit
import dev.mi6e4ka.openstore.BuildConfig

object DeviceIdManager {
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        
        if (deviceId == null) {
            deviceId = generateDeviceId()
            prefs.edit { putString("device_id", deviceId) }
        }
        return deviceId
    }

    private fun generateDeviceId(): String {
        fun i32(x: Long): Int = ((x + 0x80000000L) and 0xFFFFFFFFL).toInt() - 0x80000000.toInt()

        fun javaStringHashCode(s: String): Int {
            var h = 0
            for (element in s) {
                h = (31 * h + element.code) and 0xFFFFFFFF.toInt()
            }
            return h
        }

        fun deviceIdSuffix(): String {
            val m = javaStringHashCode(BuildConfig.DEVICE_MANUFACTURER)
            val mo = javaStringHashCode(BuildConfig.DEVICE_MODEL)
            val h = javaStringHashCode(BuildConfig.DEVICE_HARDWARE)
            val d = javaStringHashCode(BuildConfig.DEVICE_HARDWARE)
            
            val result = i32(d.toLong() + i32((h.toLong() + i32((mo.toLong() + i32(m.toLong() * 31)) * 31.toLong())) * 31.toLong()))
            return result.toString()
        }

        val random = Random()
        val androidId = StringBuilder()
        for (i in 0 until 8) {
            androidId.append(String.format("%02x", random.nextInt(256)))
        }
        
        return "$androidId-${deviceIdSuffix()}"
    }
}
