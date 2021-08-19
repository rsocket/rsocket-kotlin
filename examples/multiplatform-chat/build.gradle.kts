/*
 * Copyright 2015-2020 the original author or authors.
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
    `kotlin-multiplatform`
    `kotlinx-atomicfu`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("serverJvm")
    jvm("clientJvm")
    js("clientJs", IR) {
        browser {
            binaries.executable()
        }
        nodejs {
            binaries.executable()
        }
    }
    when {
        HostManager.hostIsLinux -> linuxX64("clientNative")
        HostManager.hostIsMingw -> null //no native support for TCP mingwX64("clientNative")
        HostManager.hostIsMac   -> macosX64("clientNative")
        else                    -> null
    }?.binaries {
        executable {
            entryPoint = "main"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.rsocketCore)
                implementation(libs.kotlinx.serialization.protobuf)
            }
        }

        val clientMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(projects.rsocketTransportKtorClient)
            }
        }

        val serverJvmMain by getting {
            dependencies {
                implementation(projects.rsocketTransportKtorServer)
                implementation(libs.ktor.server.cio)
            }
        }

        val clientJvmMain by getting {
            dependsOn(clientMain)
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val clientJsMain by getting {
            dependsOn(clientMain)
        }

        if (!HostManager.hostIsMingw) {
            val clientNativeMain by getting {
                dependsOn(clientMain)
            }
        }
    }
}
