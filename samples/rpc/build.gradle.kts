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
}

val rsocketVersion: String by rootProject
val kotlinxCoroutinesVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    when {
        HostManager.hostIsLinux -> linuxX64("native")
        HostManager.hostIsMingw -> mingwX64("native")
        HostManager.hostIsMac   -> macosX64("native")
    }

    sourceSets {
        commonMain {
            dependencies {
                api("io.rsocket.kotlin:rsocket-core:$rsocketVersion")
                api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.rsocket.kotlin:rsocket-transport-local:$rsocketVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion")
            }
        }
    }
}
