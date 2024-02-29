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

import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(8)

    targets.configureEach {
        compilations.configureEach {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
            }
        }
    }

    sourceSets.configureEach {
        languageSettings {
            progressiveMode = true
            if (name.contains("test", ignoreCase = true)) optInForTest()
        }
    }
}


if (providers.gradleProperty("rsocketbuild.skipTests").map(String::toBoolean).getOrElse(false)) {
    tasks.withType<AbstractTestTask>().configureEach { onlyIf { false } }
}

if (providers.gradleProperty("rsocketbuild.skipNativeLink").map(String::toBoolean).getOrElse(false)) {
    tasks.withType<KotlinNativeLink>().configureEach { onlyIf { false } }
}
