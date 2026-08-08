import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The key shake-to-report posts issues with. Never in the repository:
 * local.properties is git-ignored and CI hands it in from a secret. An empty
 * string is a working build -- reports queue on the phone and go out from a
 * later build that has the key.
 */
val reportToken: String = run {
    val local = rootProject.file("local.properties")
    val fromFile = if (local.exists()) {
        Properties().apply { local.inputStream().use { load(it) } }.getProperty("reportToken")
    } else null
    fromFile ?: System.getenv("REPORT_TOKEN") ?: ""
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
        versionName = "1.22.0"

        ndk { abiFilters += "arm64-v8a" }

        // LightReport.install reads this at startup.
        buildConfigField("String", "REPORT_TOKEN", "\"$reportToken\"")

        buildConfigField(
            "String",
            "INDEX_URL",
            "\"https://brightmarket.gzl.dev/index-v1.json\""
        )
    }

    // The release key used to live in this repository with its password written
    // three lines below it, which meant anyone could produce an APK that Android
    // would accept as an update to this one. It is now a CI secret: the workflow
    // decodes it to keystore/brightmarket.jks, which is gitignored.
    //
    // A build without the secret still works and still produces an installable
    // APK -- it is just signed with the local debug key and will not update over
    // a release. That is the right failure: an unsigned or differently-signed
    // build that announces itself is better than one that silently isn't the
    // real thing.
    val keystoreFile = rootProject.file("keystore/brightmarket.jks")
    val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD") ?: ""
    val canSignRelease = keystoreFile.exists() && keystorePassword.isNotEmpty()

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "brightmarket"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
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
    // org.json ships in android.jar as method stubs that throw. isReturnDefaultValues
    // turns those throws into silent nulls, which is worse -- every JSON parse
    // returns null and the tests NPE far from the cause. The real fix is putting an
    // actual org.json implementation on the unit-test classpath (see dependencies),
    // which shadows the stubs; this flag stays only for the rest of android.jar.
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

    // QR scanning, carried over from LightQR: CameraX preview + frame analysis,
    // decoded by ZXing on the luminance plane. ML Kit is not an option -- LightOS
    // ships without Google Play Services.
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.zxing:core:3.5.3")

    // The shared Light layer: the hardware wheel and shake-to-report, which
    // every other app in the portfolio already uses. Reimplementing either here
    // would be a second copy to keep in step with the first.
    implementation("com.gios:light-common:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    // Real org.json for JVM tests, shadowing android.jar's stubs. Without this
    // Index.parse and Obtainium.parse return null under test while working fine
    // on device -- the tests would be measuring the stub, not the code.
    testImplementation("org.json:json:20240303")
}
