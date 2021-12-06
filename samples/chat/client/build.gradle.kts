import org.jetbrains.kotlin.konan.target.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
}

val rsocketVersion: String by rootProject
val ktorVersion: String by rootProject

application {
    mainClass.set("io.rsocket.kotlin.samples.chat.client.AppKt")
}

kotlin {
    jvm {
        withJava()
    }
    js(IR) {
        browser {
            binaries.executable()
        }
        nodejs {
            binaries.executable()
        }
    }
    when {
        HostManager.hostIsLinux -> linuxX64("native")
        HostManager.hostIsMingw -> null //no native support for TCP in ktor mingwX64("clientNative")
        HostManager.hostIsMac   -> macosX64("native")
        else                    -> null
    }?.binaries {
        executable {
            entryPoint = "io.rsocket.kotlin.samples.chat.client.main"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":api"))
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:$rsocketVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktorVersion")
            }
        }
    }
}
