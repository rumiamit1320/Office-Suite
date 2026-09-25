plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace="com.docuflow.android"
    compileSdk=36
    ndkVersion="27.2.12479018"
    defaultConfig {
        applicationId="com.docuflow.android"
        minSdk=26
        targetSdk=36
        versionCode=1
        versionName="0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildFeatures { compose=true }
    externalNativeBuild { cmake { path=file("src/main/cpp/CMakeLists.txt"); version="4.1.2" } }
    packaging { jniLibs.useLegacyPackaging=true }
}
dependencies {
    val bom=platform("androidx.compose:compose-bom:2025.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
