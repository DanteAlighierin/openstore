import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystoreFileExists = rootProject.file("keystore.properties").exists();
if (keystoreFileExists) {
    keystoreProperties.load(rootProject.file("keystore.properties").inputStream())
}

val gitCommitShort: String = run {
    try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotEmpty()) output else "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

android {
    namespace = "dev.mi6e4ka.openstore"
    compileSdk = 37
    ndkVersion = "27.3.13750724"

    androidResources {
        localeFilters.addAll(arrayOf("ru", "en"))
    }

    defaultConfig {
        applicationId = "dev.mi6e4ka.openstore"
        minSdk = 21
        targetSdk = 37
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as String? ?: ("git-" + gitCommitShort)
        
        buildConfigField("int", "RUSTORE_VER_CODE", "1105003")
        buildConfigField("String", "RUSTORE_VER_NAME", "\"1.107.0.3\"")
        buildConfigField("String", "DEVICE_MANUFACTURER", "\"Google\"")
        buildConfigField("String", "DEVICE_MODEL", "\"Pixel 8 Pro\"")
        buildConfigField("String", "DEVICE_HARDWARE", "\"husky\"")
        buildConfigField("int", "CLIENT_SDK_VERSION", "37")
        buildConfigField("int", "CLIENT_ANDROID_VERSION", "17")
    }

    signingConfigs {
        if (keystoreFileExists) {
            create("release") {
                storeFile = keystoreProperties["storeFile"]?.let { file(it as String) }
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("nightly") {
            initWith(getByName("release"))
            versionNameSuffix = "-nightly"
            applicationIdSuffix = ".nightly"
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["appName"] = "OpenStore Nightly"
        }
        debug {
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "OpenStore Debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
	}
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.at.lz4.java)
    implementation(libs.material3)
    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.documentfile)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}