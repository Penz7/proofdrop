plugins {
    alias(libs.plugins.proofdrop.android.feature)
}

android {
    namespace = "com.penz7.proofdrop.feature.fleet"
}

dependencies {
    implementation(libs.maplibre.android)
}
