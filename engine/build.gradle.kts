plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.anthony.djapp.engine"
        compileSdk = 37
        minSdk = 26
    }

    // Alvo JVM só para rodar os testes do DSP rapidamente no desktop.
    jvm()

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
