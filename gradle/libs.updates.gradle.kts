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

/**
 * run to check for dependencies:
 *  ./gradlew :dependencyUpdates --init-script gradle/libs.updates.gradle.kts --no-configure-on-demand
 */

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.github.ben-manes:gradle-versions-plugin:+")
    }
}

allprojects {
    println("Project: $name / ${rootProject.name}")
    apply<com.github.benmanes.gradle.versions.VersionsPlugin>()

    // for root project add dependency on included builds
    if (name == "rsocket-kotlin") tasks.named("dependencyUpdates") {
        gradle.includedBuilds.forEach {
            dependsOn(it.task(":dependencyUpdates"))
        }
    }
}
