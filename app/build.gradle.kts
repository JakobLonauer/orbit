import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.roborazzi)
}

val orbitVersionCode = (findProperty("orbitVersionCode") as String?)?.toIntOrNull() ?: 1
val keystorePath: String? = System.getenv("ORBIT_KEYSTORE_PATH")

android {
    namespace = "io.github.thelony97.orbit"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.thelony97.orbit"
        minSdk = 34
        targetSdk = 36
        versionCode = orbitVersionCode
        versionName = "1.0.$orbitVersionCode"
        buildConfigField("String", "GITHUB_REPO", "\"JakobLonauer/orbit\"")
        // MapLibre ships native code; every phone of the last decade is ARM.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ORBIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ORBIT_KEY_ALIAS")
                keyPassword = System.getenv("ORBIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("17")
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging.resources {
        excludes += "/META-INF/AL2.0"
        excludes += "/META-INF/LGPL2.1"
        excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        excludes += "/META-INF/LICENSE.md"
        excludes += "/META-INF/NOTICE.md"
        excludes += "/META-INF/LICENSE.txt"
        excludes += "/META-INF/NOTICE.txt"
    }
}

tasks.withType<Test>().configureEach {
    // Real hardware-accelerated rendering in Robolectric (blur, shadows, RenderEffect).
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Custom Tabs for portals (PLAN 3.7: Chrome sign-ins stay, Zurück führt zu Orbit).
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.maplibre.android)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    // QR code of a shared contact (PLAN 3.3); pure Java, encoder only.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
