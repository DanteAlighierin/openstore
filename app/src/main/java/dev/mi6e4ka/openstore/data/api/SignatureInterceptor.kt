package dev.mi6e4ka.openstore.data.api

import android.content.Context
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.edit
import dev.mi6e4ka.openstore.BuildConfig

class SignatureInterceptor(context: Context) : Interceptor {
    private val prefs = context.getSharedPreferences("sig_storage", Context.MODE_PRIVATE)
    private val deviceId = DeviceIdManager.getOrCreateDeviceId(context)
    private val HMAC_KEY = Base64.decode("K+eeiCbnVFnZ71KEVal0g5siHaX6v6drh8upeLgEPoU=", Base64.NO_WRAP)
    private val APK_CERT = Base64.decode("Zh8ggo73gN4LebxZ8mowhkMWNV8w5Pkc+hSiB5GDmRQ=", Base64.NO_WRAP)

    override fun intercept(chain: Interceptor.Chain): Response {
        val sig = synchronized(this) { prefs.getString("sig", null) ?: refresh(chain) }
        
        val resp = chain.proceed(chain.request().newBuilder().apply {
            header("deviceId", deviceId)
            if (sig != null) {
                header("X-Client-Signature", sig)
            }
        }.build())

        if (resp.code == 419) {
            resp.close()
            val newSig = synchronized(this) { refresh(chain) }
            return chain.proceed(chain.request().newBuilder().apply {
                header("deviceId", deviceId)
                if (newSig != null) {
                    header("X-Client-Signature", newSig)
                }
            }.build())
        }
        return resp
    }

    private fun refresh(chain: Interceptor.Chain): String? = try {
        val content = ByteArray(0)
        val req = Request.Builder()
            .url("https://api.rustore.ru/v1/secure/nonce")
            .header("deviceId", deviceId)
            .post(content.toRequestBody(null, 0, content.size))
            .build()

        val body = chain.proceed(req).use { it.body?.string() }
        val nonce = Base64.decode(JSONObject(body!!).getString("nonce"), Base64.NO_WRAP)

        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(HMAC_KEY, "HmacSHA256")) }
        val sig = Base64.encodeToString(mac.doFinal(nonce + APK_CERT), Base64.NO_WRAP)
        
        prefs.edit { putString("sig", sig) }
        sig
    } catch (e: Exception) {
        null
    }
}
