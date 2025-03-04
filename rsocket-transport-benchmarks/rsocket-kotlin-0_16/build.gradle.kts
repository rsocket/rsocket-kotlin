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

import kotlinx.benchmark.gradle.*
import rsocketbuild.*

plugins {
    id("rsocketbuild.multiplatform-benchmarks")
}

kotlin {
    jvmTarget()

    macosX64()
    macosArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.rsocketTransportBenchmarksBase)

            implementation("io.rsocket.kotlin:rsocket-transport-local:0.16.0")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:0.16.0")
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("macosArm64")
        register("macosX64")
        register("linuxX64")
    }
    registerTransportBenchmarks("RSocketKotlin_0_16_", listOf("local", "ktorTcp")) { transport ->
        if (transport == "local") {
            param("dispatcher", "DEFAULT", "UNCONFINED")
        }
    }
}

// the old ktor TCP implementation has some issues on native...
tasks.withType<NativeBenchmarkExec>()
    .named { it.contains("KtorTcp") }
    .configureEach { onlyIf { false } }
