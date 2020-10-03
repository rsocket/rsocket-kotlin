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
}

val ktorVersion: String by rootProject
val kotlinxCoroutinesVersion: String by rootProject
val turbineVersion: String by rootProject

kotlin {
    jvm()
    js()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rsocket-core"))
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
                api("io.ktor:ktor-utils:$ktorVersion")
                api("app.cash.turbine:turbine:$turbineVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                api(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                api(kotlin("test-js"))
            }
        }
    }
}
