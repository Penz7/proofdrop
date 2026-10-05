plugins {
    alias(libs.plugins.proofdrop.android.library)
    alias(libs.plugins.proofdrop.android.hilt)
}

android {
    namespace = "com.penz7.proofdrop.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.evidence)
    api(projects.core.ble)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
