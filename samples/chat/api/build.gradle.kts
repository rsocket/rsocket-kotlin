import org.jetbrains.kotlin.konan.target.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val rsocketVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    when {
        HostManager.hostIsLinux -> linuxX64("native")
        HostManager.hostIsMingw -> null //no native support for TCP in ktor mingwX64("native")
        HostManager.hostIsMac   -> macosX64("native")
        else                    -> null
    }

    sourceSets {
        commonMain {
            dependencies {
                api("io.rsocket.kotlin:rsocket-core:$rsocketVersion")
                api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
            }
        }
    }
}
