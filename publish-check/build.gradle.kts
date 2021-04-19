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

val publishCheckVersion: String by project

kotlin {
    //all targets depends on core and local transport

    jvm() //depends on ktor client and server
    js(IR) { //depends on ktor client
        browser()
        nodejs()
    }

    mingwX64() //depends only on common
    val hostTargets = listOf(linuxX64(), macosX64())
    val iosTargets = listOf(iosArm32(), iosArm64(), iosX64())
    val tvosTargets = listOf(tvosArm64(), tvosX64())
    val watchosTargets = listOf(watchosArm32(), watchosArm64(), watchosX86())
    val nativeTargets = hostTargets + iosTargets + tvosTargets + watchosTargets //depends on ktor client

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-core:$publishCheckVersion")
                implementation("io.rsocket.kotlin:rsocket-transport-local:$publishCheckVersion")

                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))
            }
        }

        val nonWinCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor:$publishCheckVersion")
            }
        }

        val clientMain by creating {
            dependsOn(nonWinCommonMain)
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:$publishCheckVersion")
            }
        }

        val jsMain by getting {
            dependsOn(clientMain)
            dependencies {
                api(kotlin("test-js"))
            }
        }

        val jvmMain by getting {
            dependsOn(clientMain)
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-server:$publishCheckVersion")

                api(kotlin("test-junit"))
            }
        }

        val nativeMain by creating {
            dependsOn(clientMain)
        }

        nativeTargets.forEach {
            sourceSets["${it.name}Main"].dependsOn(nativeMain)
        }
    }
}
