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
    alias(libs.plugins.kotlin.ksp)
}

// FFmpeg's natives for the machine doing the build: each desktop installer is built on its own OS.
val ffmpegPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> if (arm) "macosx-arm64" else "macosx-x86_64"
        os.contains("win") -> "windows-x86_64"
        else -> if (arm) "linux-arm64" else "linux-x86_64"
    }
}
val ffmpegDesktop: String = libs.versions.ffmpegDesktop.get()

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
            api(libs.datastore)
            implementation(libs.room.runtime)
            implementation(libs.cmp.components.resources)
            // the YouTube Music models (songs, albums, artists) every platform's screens show
            api(project(":providers:innertube"))
            implementation(libs.coil)
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
            implementation(libs.sqlite.bundled)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.coil.network.okhttp)
            // stream resolution, the same library the Android app asks first
            implementation(libs.innertubex.desktop)
            // decoding: JavaCV's FFmpeg grabber without the rest of JavaCV's camera and vision stack
            implementation("org.bytedeco:javacv:${libs.versions.javacpp.get()}") { isTransitive = false }
            implementation(libs.javacpp)
            implementation("org.bytedeco:ffmpeg:$ffmpegDesktop")
            implementation("org.bytedeco:ffmpeg:$ffmpegDesktop:$ffmpegPlatform")
            implementation("org.bytedeco:javacpp:${libs.versions.javacpp.get()}:$ffmpegPlatform")
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
            implementation(libs.innertubex)
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.coil.network.ktor)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

val prepareIosFonts = tasks.register<Sync>("prepareIosFonts") {
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/font")) {
        include("inter_regular.ttf", "inter_semibold.ttf")
        into("font")
    }
    into(layout.buildDirectory.dir("generated/iosComposeFonts"))
}

compose.resources {
    customDirectory("iosMain", layout.dir(prepareIosFonts.map { it.destinationDir }))
    // a library does not generate Res by default; every platform's UI reads strings through it
    generateResClass = always
    publicResClass = false
    packageOfResClass = "com.mochyy.mochymusic.shared.resources"
}
