plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.anthony.djapp.shared"
        compileSdk = 37
        minSdk = 26

        // testes de unidade na JVM (a lógica de SYNC do controller não depende de tela)
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        commonMain.dependencies {
            api(project(":engine"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.ui)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
