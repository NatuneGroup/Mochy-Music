// Shared multiplatform code for Mochy (fork of Mochy Music by realidkroo,
// github.com/realidkroo/mochy-music). One library for every platform, in Metrolist's shape:
// the Android app (:app), the desktop launcher (:desktopApp) and the iOS app all depend on it
// and add nothing to it. Code moves here from :app one area at a time.
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    // AGP ships multiplatform support only as a library plugin, so the installable APK stays in :app
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("androidLibrary") {
        namespace = "com.mochyy.mochymusic.shared"
        compileSdk = 37
        minSdk = 24
        // compose resources (strings, later fonts and icons) ship inside the APK through this
        androidResources.enable = true
    }

    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    // Declared now so commonMain stays iOS-clean; they compile only on a macOS host.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            implementation(libs.cmp.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            // Providers take an injected HttpClient; each platform picks the engine, timeouts and proxy.
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.common)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.swing)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

compose.resources {
    // a library does not generate Res by default; every platform's UI reads strings through it
    generateResClass = always
    publicResClass = false
    packageOfResClass = "com.mochyy.mochymusic.shared.resources"
}
