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

plugins {
    id("rsocket.template.test")
    id("rsocket.target.all")
    id("kotlinx-atomicfu")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("test"))
                api(projects.rsocketCore)
                implementation(projects.rsocketInternalIo)

                api(libs.kotlinx.coroutines.test)
                api(libs.turbine)
            }
        }
        jvmMain {
            dependencies {
                api(kotlin("test-junit"))
            }
        }
        jsMain {
            dependencies {
                api(kotlin("test-js"))
            }
        }
    }
}
