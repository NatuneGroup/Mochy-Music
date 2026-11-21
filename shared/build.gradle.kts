// Shared multiplatform code for Mochy (fork of Mochy Music by realidkroo,
// github.com/realidkroo/mochy-music). Started with the lyrics providers ported from
// Natune; Android consumes the JVM target, desktop and iOS targets join as the app moves over.
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // Providers take an injected HttpClient; the host picks the engine, timeouts and proxy.
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
