plugins {
    alias(libs.plugins.proofdrop.android.library)
    alias(libs.plugins.proofdrop.android.compose)
}

android {
    namespace = "com.penz7.proofdrop.core.designsystem"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
