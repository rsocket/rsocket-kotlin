/*
 * Copyright 2015-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    js("browser") {
        browser {
            binaries.executable()
        }
    }
    js("nodejs") {
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
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-client:$rsocketVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        findByName("nativeMain")?.apply {
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val browserMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktorVersion")
            }
        }
        val nodejsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation("io.rsocket.kotlin:rsocket-transport-nodejs-tcp:$rsocketVersion")
            }
        }
    }
}
