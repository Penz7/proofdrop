plugins {
    alias(libs.plugins.proofdrop.android.library)
    alias(libs.plugins.proofdrop.android.hilt)
}

android {
    namespace = "com.penz7.proofdrop.core.ble"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
