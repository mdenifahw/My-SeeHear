import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

kotlin {
    android {
        namespace = "id.sch.osis.paperless.shared"
        compileSdk = 36
        minSdk = 24
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    if (isMacHost) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.icons.extended)
            implementation(libs.compose.components.resources)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
            }
        }
        if (isMacHost) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "id.sch.osis.paperless.resources"
    generateResClass = always
}
