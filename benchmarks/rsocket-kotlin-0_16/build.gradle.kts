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

import kotlinx.benchmark.gradle.*
import org.gradle.kotlin.dsl.benchmark
import rsocketbuild.*

plugins {
    id("rsocketbuild.multiplatform-benchmarks")
}

kotlin {
    jvmTarget()

    // no mingw because of cio
    macosX64()
    macosArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.benchmarksShared)
            implementation("io.rsocket.kotlin:rsocket-transport-local:0.16.0")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:0.16.0")
        }
    }
}

benchmark {
    targets {
        register("jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = libs.versions.jmh.get()
        }
        register("macosArm64")
        register("macosX64")
        register("linuxX64")
    }
    registerBenchmarks("RSocketKotlin_0_16_", listOf("local", "ktorTcp")) { transport ->
        if (transport == "local") {
            param("dispatcher", "DEFAULT", "UNCONFINED")
        }
    }
}
