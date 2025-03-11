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
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set("io.rsocket.kotlin.samples.chat.server.AppKt")
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
                entryPoint = "io.rsocket.kotlin.samples.chat.server.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":api"))

            implementation("org.jetbrains.kotlinx:atomicfu:0.27.0") // for Atomic (drop after Kotlin 2.2)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2") // for Instant (drop after Kotlin 2.2)
            implementation("io.ktor:ktor-utils:$ktorVersion") //for concurrent map implementation and shared list

            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-server:$rsocketVersion")
            implementation("io.ktor:ktor-server-cio:$ktorVersion")
        }
    }
}
