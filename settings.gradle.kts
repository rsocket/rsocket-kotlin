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

pluginManagement {
    repositories {
        maven("https://dl.bintray.com/kotlin/kotlinx")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.jfrog.bintray") version "1.8.5"
        id("com.jfrog.artifactory") version "4.17.2"
        id("com.github.ben-manes.versions") version "0.33.0"
    }
}

plugins {
    id("com.gradle.enterprise") version "3.4.1"
}

rootProject.name = "rsocket-kotlin"

include("benchmarks")
include("playground")

include("rsocket-core")
include("rsocket-test")

include("rsocket-transport-local")

include("rsocket-transport-ktor")
include("rsocket-transport-ktor-client")
include("rsocket-transport-ktor-server")
project(":rsocket-transport-ktor-client").projectDir = file("rsocket-transport-ktor/rsocket-transport-ktor-client")
project(":rsocket-transport-ktor-server").projectDir = file("rsocket-transport-ktor/rsocket-transport-ktor-server")

fun includeExample(name: String) {
    include("examples:$name")
}

includeExample("nodejs-tcp-transport")
includeExample("interactions")
includeExample("multiplatform-chat")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
