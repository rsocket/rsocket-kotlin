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
    id("kotlinx-atomicfu")

    id("maven-publish")
    id("com.jfrog.bintray")
    id("com.jfrog.artifactory")
}

kotlin {
    jvm()
    js()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.ktor:ktor-io:1.4.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("app.cash.turbine:turbine:0.2.1")
                implementation("io.ktor:ktor-utils:1.4.0")
                implementation(project(":rsocket-transport-local"))
            }
        }
    }
}

atomicfu {
    variant = "FU"
}
