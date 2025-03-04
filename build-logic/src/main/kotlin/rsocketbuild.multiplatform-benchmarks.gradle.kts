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

import gradle.kotlin.dsl.accessors._5e24a5abecdb427308a6c627b6f26b4a.allOpen
import gradle.kotlin.dsl.accessors._5e24a5abecdb427308a6c627b6f26b4a.benchmark
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    id("rsocketbuild.multiplatform-base")
    kotlin("plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets.configureEach {
        if (name == "jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
    }
}

if (providers.gradleProperty("rsocketbuild.skipBenchmarkTasks").map(String::toBoolean).getOrElse(false)) {
    tasks.named { it.endsWith("Benchmark") }.configureEach { onlyIf { false } }
}
