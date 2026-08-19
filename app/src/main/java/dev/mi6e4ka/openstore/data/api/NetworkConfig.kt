package dev.mi6e4ka.openstore.data.api

import android.content.Context
import android.util.Base64
import android.util.Log
import dev.mi6e4ka.openstore.R
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import dev.mi6e4ka.openstore.BuildConfig

object NetworkConfig {

    private val DEFAULT_PINS = listOf(
        "sha256/u1IIbQY56NszJ3Wsj06ENdks6wD04k8o/A6r4kB3LoA=", // Harica ECC Root CA 2015
        "sha256/UMyGupbbMmPHmkPq0HVT2fVmWeaQfnLYwCZjehzchdw=", // Harica RSA Root CA 2015
        "sha256//HhDAOyN9NPRutdjg1GCkY1Sqf8COL32laHNm9uYMhw=", // Harica ECC TLS Root 2021
        "sha256/aTyaprJFs7AmFjd1CGPq22wkihblLW9LyQyGu/MtcEI=", // Harica RSA TLS Root 2021

        "sha256/FfFKxFycfaIz00eRZOgTf+Ne4POK6FgYPwhBDqgqxLQ=", // Starfield Root Certificate Authority
        "sha256/gI1os/q0iEpflxrOfRBVDXqVoWN3Tz7Dav/7IT++THQ=", // Starfield Root Certificate Authority G2
        "sha256/ww9H85szAjIB4B/FOLdXky5TsrNLh3jRxBxclRz4qlY=", // Starfield TLS Root CA R1

        "sha256/VjLZe/p3W/PJnd6lL8JVNBCGQBZynFLdZSTIqcO0SJ8=", // GoDaddy Class 2 CA Root
        "sha256/Ko8tivDrEjiY90yGasP6ZpBU4jwXvHqVvQI0GS3GNdA=", // GoDaddy Class 2 CA Root G2
        "sha256/gKjJIacZSuY6bbDnu13WF/rufps/FKwGIaqxRb5nKZA=", // GoDaddy TLS Root CA R1
    )

    fun provideOkHttpClient(context: Context): OkHttpClient {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val mode = prefs.getString("ssl_pinning_mode", "disabled") ?: "disabled"
        val deviceId = DeviceIdManager.getOrCreateDeviceId(context)
        val userAgent = "RuStore/${BuildConfig.RUSTORE_VER_NAME} (Android ${BuildConfig.CLIENT_ANDROID_VERSION}; SDK ${BuildConfig.CLIENT_SDK_VERSION}; arm64-v8a; ${BuildConfig.DEVICE_MANUFACTURER} ${BuildConfig.DEVICE_MODEL}; ru)"

        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    .header("ruStoreVerCode", BuildConfig.RUSTORE_VER_CODE.toString())
                    .header("ruStoreVerName", BuildConfig.RUSTORE_VER_NAME)
                    .header("User-Agent", userAgent)
                    .header("androidSdkVer", BuildConfig.CLIENT_SDK_VERSION.toString())
                    .build()
                chain.proceed(newRequest)
            }
            .addInterceptor(SignatureInterceptor(context))

        when (mode) {
            "enabled" -> {
                builder.certificatePinner(createPinner(DEFAULT_PINS))
            }
            "custom" -> {
                val customCert = loadCustomCertificate(context)
                val pins = if (customCert != null) {
                    DEFAULT_PINS + "sha256/${calculateSpkiHash(customCert)}"
                } else {
                    DEFAULT_PINS
                }
                
                builder.certificatePinner(createPinner(pins))
                configureCompositeTrust(builder, customCert)
            }
            else -> {
                // Standard SSL verification (default) or "disabled"
            }
        }

        return builder.build()
    }

    private fun createPinner(pins: List<String>): CertificatePinner {
        val pinnerBuilder = CertificatePinner.Builder()
        pins.distinct().forEach { pin ->
            pinnerBuilder.add("*.rustore.ru", pin)
        }
        return pinnerBuilder.build()
    }

    private fun loadCustomCertificate(context: Context): X509Certificate? {
        return try {
            val certInputStream: InputStream = context.resources.openRawResource(R.raw.russian_trusted_root_ca)
            if (certInputStream.available() <= 0) return null
            
            val cf = CertificateFactory.getInstance("X.509")
            cf.generateCertificate(certInputStream) as X509Certificate
        } catch (e: Exception) {
            Log.e("NetworkConfig", "Failed to load custom certificate", e)
            null
        }
    }

    private fun calculateSpkiHash(cert: X509Certificate): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val spkiBytes = cert.publicKey.encoded
        return Base64.encodeToString(sha256.digest(spkiBytes), Base64.NO_WRAP)
    }

    private fun configureCompositeTrust(builder: OkHttpClient.Builder, customCert: X509Certificate?) {
        val systemTrustManager = getDefaultTrustManager()
        
        val customTrustManager = if (customCert != null) {
            val customKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("custom-ca", customCert)
            }
            val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            customTmf.init(customKeyStore)
            customTmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        } else null

        val compositeTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                systemTrustManager.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    systemTrustManager.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    if (customTrustManager != null) {
                        customTrustManager.checkServerTrusted(chain, authType)
                    } else {
                        throw e
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return if (customTrustManager != null) {
                    systemTrustManager.acceptedIssuers + customTrustManager.acceptedIssuers
                } else {
                    systemTrustManager.acceptedIssuers
                }
            }
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(compositeTrustManager), SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, compositeTrustManager)
    }

    private fun getDefaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }
}
