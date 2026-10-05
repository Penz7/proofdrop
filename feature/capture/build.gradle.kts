plugins {
    alias(libs.plugins.proofdrop.android.feature)
}

android {
    namespace = "com.penz7.proofdrop.feature.capture"
}

dependencies {
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}
