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

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.4.10"
    id("kotlinx-atomicfu")
}

kotlin {
    jvm("serverJvm")
    jvm("clientJvm")
    js("clientJs", LEGACY) {
        browser {
            binaries.executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":rsocket-core"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.0.0-RC")
            }
        }

        val clientMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(project(":rsocket-transport-ktor-client"))
            }
        }

        val serverJvmMain by getting {
            dependencies {
                implementation(project(":rsocket-transport-ktor-server"))
                implementation("io.ktor:ktor-server-cio:1.4.1")
            }
        }

        val clientJvmMain by getting {
            dependsOn(clientMain)
            dependencies {
                implementation("io.ktor:ktor-client-cio:1.4.1")
            }
        }

        val clientJsMain by getting {
            dependsOn(clientMain)
            dependencies {
                implementation("io.ktor:ktor-client-js:1.4.1")
            }
        }
    }
}
