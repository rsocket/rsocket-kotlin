/*
 * Copyright 2015-2023 the original author or authors.
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

import org.jetbrains.kotlin.gradle.targets.js.yarn.*

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        // kotlinx.atomicfu should be on classpath
        //  it's an implementation detail of kotlinx.atomicfu gradle plugin
        classpath(kotlinLibs.gradle.plugin)
        classpath(libs.build.kotlinx.atomicfu)
    }
}

plugins {
    id("build-parameters")
    // for now BCV uses `allProjects` internally, so we can't apply it just to specific subprojects
    alias(libs.plugins.kotlinx.bcv)
}

apiValidation {
    ignoredProjects.addAll(
        listOf(
            "rsocket-test",
            "rsocket-transport-tests"
        )
    )
}

plugins.withType<YarnPlugin> {
    yarn.apply {
        lockFileDirectory = file("gradle/js/yarn")
        yarnLockMismatchReport = YarnLockMismatchReport.WARNING
    }
}
