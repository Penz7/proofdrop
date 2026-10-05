plugins {
    alias(libs.plugins.proofdrop.android.library)
    alias(libs.plugins.proofdrop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.penz7.proofdrop.core.network"
}

dependencies {
    api(projects.core.model)
    api(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
}
