import java.util.Properties

plugins {
    alias(libs.plugins.proofdrop.android.application)
    alias(libs.plugins.proofdrop.android.compose)
    alias(libs.plugins.proofdrop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Upload-key credentials live outside git (see docs/PLAY_STORE.md). Without them, release builds
// fall back to the debug key so CI and reviewers can still build a runnable release APK.
val keystoreProps = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.penz7.proofdrop"

    defaultConfig {
        applicationId = "com.penz7.proofdrop"
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"https://github.com/Penz7/proofdrop/blob/main/docs/PRIVACY.md\"",
        )
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("upload") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.feature.auth)
    implementation(projects.feature.orders)
    implementation(projects.feature.capture)
    implementation(projects.feature.checkout)
    implementation(projects.feature.fleet)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // End-to-end tests that drive the real app on a device against a running backend.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
