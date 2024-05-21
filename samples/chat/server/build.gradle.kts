/*
 * Copyright 2015-2024 the original author or authors.
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
    application
}

val rsocketVersion: String by rootProject
val ktorVersion: String by rootProject

application {
    mainClass.set("io.rsocket.kotlin.samples.chat.server.AppKt")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        withJava()
    }
    js {
        nodejs()
        binaries.executable()
    }
    linuxX64()
    macosX64()
    macosArm64()
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

            implementation("io.ktor:ktor-utils:$ktorVersion") //for concurrent map implementation and shared list
        }
        jvmMain.dependencies {
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-server:$rsocketVersion")
            implementation("io.ktor:ktor-server-cio:$ktorVersion")
        }
        nativeMain.dependencies {
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-server:$rsocketVersion")
            implementation("io.ktor:ktor-server-cio:$ktorVersion")
        }
        jsMain.dependencies {
            implementation("io.rsocket.kotlin:rsocket-transport-nodejs-tcp:$rsocketVersion")
        }
    }
}
