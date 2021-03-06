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
    kotlin("multiplatform")
}

val ktorVersion: String by rootProject

kotlin {
    jvm()
    js(IR) {
        browser {
            binaries.executable()
        }
    }
    when {
        HostManager.hostIsLinux -> linuxX64("native")
//        HostManager.hostIsMingw -> mingwX64("native") //no native support for TCP
        HostManager.hostIsMac   -> macosX64("native")
        else                    -> null
    }?.binaries {
        executable {
            entryPoint = "main"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":rsocket-core"))
                implementation(project(":rsocket-transport-local"))
                implementation(project(":rsocket-transport-ktor-client"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":rsocket-transport-ktor-server"))

                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktorVersion") //for WS support
            }
        }
    }
}
