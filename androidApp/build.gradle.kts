plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.anthony.djapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.anthony.djapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":engine"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
