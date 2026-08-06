plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gios.brightmarket"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gios.brightmarket"
        // Wireless debugging (and therefore the future :adb silent-install module)
        // needs Android 11. The rest of the portfolio targets 29; this one can't.
        minSdk = 30
        targetSdk = 35
        // CI overwrites both from the workflow run number; see .github/workflows/build.yml
        versionCode = 1
        versionName = "1.0.0"

        ndk { abiFilters += "arm64-v8a" }

        buildConfigField(
            "String",
            "INDEX_URL",
            "\"https://gi-os.github.io/brightmarket-index/index-v1.json\""
        )
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/brightmarket.jks")
            storePassword = "brightmarket"
            keyAlias = "brightmarket"
            keyPassword = "brightmarket"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Same committed key as debug, so either APK upgrades over the other.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Daily update checks. WorkManager rather than an alarm: the check is
    // deferrable and network-gated, which is exactly what it's for.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}
