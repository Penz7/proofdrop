plugins {
    alias(libs.plugins.proofdrop.android.application)
    alias(libs.plugins.proofdrop.android.compose)
    alias(libs.plugins.proofdrop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.penz7.proofdrop"

    defaultConfig {
        applicationId = "com.penz7.proofdrop"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Play signing: create keystore.properties locally (never commit it). Falls back to debug
            // signing so `assembleRelease` still works on CI and for reviewers.
            signingConfig = signingConfigs.getByName("debug")
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
}
