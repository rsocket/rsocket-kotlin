/*
 * Copyright 2015-2025 the original author or authors.
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

import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val rsocketVersion: String by rootProject
val ktorVersion: String by rootProject

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            this.mainClass.set("io.rsocket.kotlin.samples.chat.client.AppKt")
        }
    }
    js {
        nodejs()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.executable()
    }
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "io.rsocket.kotlin.samples.chat.client.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":api"))

            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-client:$rsocketVersion")
            implementation("io.ktor:ktor-client-cio:$ktorVersion")
        }
    }
}
