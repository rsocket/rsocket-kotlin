import org.jetbrains.kotlin.konan.target.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
}

val rsocketVersion: String by rootProject
val ktorVersion: String by rootProject

application {
    mainClass.set("io.rsocket.kotlin.samples.chat.server.AppKt")
}

kotlin {
    jvm {
        withJava()
    }
    when {
        HostManager.hostIsLinux -> linuxX64("native")
        HostManager.hostIsMingw -> null //no native support for TCP in ktor mingwX64("clientNative")
        HostManager.hostIsMac   -> macosX64("native")
        else                    -> null
    }?.binaries {
        executable {
            entryPoint = "io.rsocket.kotlin.samples.chat.server.main"
            freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis" //TODO
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":api"))
                implementation("io.rsocket.kotlin:rsocket-transport-ktor:$rsocketVersion")
                implementation("io.ktor:ktor-utils:$ktorVersion") //for concurrent map implementation and shared list
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-server:$rsocketVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
            }
        }
    }
}
