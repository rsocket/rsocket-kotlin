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

import com.github.benmanes.gradle.versions.updates.*

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(libs.build.kotlin)
        classpath(libs.build.kotlinx.atomicfu)
    }
}

plugins {
    alias(libs.plugins.versionUpdates)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf { candidate.group == "io.ktor" && candidate.version.contains("ide-debug") }
}

configureYarn()

subprojects {
    tasks.whenTaskAdded {
        if (name.endsWith("test", ignoreCase = true)) onlyIf { !rootProject.hasProperty("skipTests") }
        if (name.startsWith("link", ignoreCase = true)) onlyIf { !rootProject.hasProperty("skipLink") }
    }
}
