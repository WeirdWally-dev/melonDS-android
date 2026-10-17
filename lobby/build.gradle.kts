import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// DS Lobby: companion app that finds nearby players on the same Wi-Fi and launches everyone into melonDS together.
android {
    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) load(file.inputStream())
            }
            (props["MELONDS_KEYSTORE"] as String?)?.let { storeFile = file(it) }
            storePassword = props["MELONDS_KEYSTORE_PASSWORD"] as String? ?: ""
            keyAlias = props["MELONDS_KEY_ALIAS"] as String? ?: ""
            keyPassword = props["MELONDS_KEY_PASSWORD"] as String? ?: ""
        }
    }

    namespace = "me.weirdwally.dslobby"
    compileSdk = AppConfig.compileSdkVersion

    defaultConfig {
        applicationId = "me.weirdwally.dslobby"
        minSdk = AppConfig.minSdkVersion
        targetSdk = AppConfig.targetSdkVersion
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlin.coroutines)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
}
