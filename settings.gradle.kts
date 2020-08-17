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
        jcenter()
        gradlePluginPortal()
    }

    plugins {
        val kotlinVersion = "1.4.0-rc"

        kotlin("multiplatform") version kotlinVersion

        kotlin("plugin.allopen") version kotlinVersion

        id("kotlinx.benchmark") version "0.2.0-dev-7"
        id("com.github.ben-manes.versions") version "0.29.0"

        id("com.jfrog.bintray") version "1.8.5"
        id("com.jfrog.artifactory") version "4.17.0"
    }

}

buildscript {
    repositories {
        mavenCentral()
        google()
        jcenter()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.0-rc") //needed by atomicfu
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.14.4")
    }

}

rootProject.name = "rsocket-kotlin"

include("benchmarks")
include("playground")

include("rsocket-core")
include("rsocket-transport-test")
include("rsocket-transport-local")
include("rsocket-transport-tcp")
include("rsocket-transport-websocket")
include("rsocket-transport-websocket-client")
include("rsocket-transport-websocket-server")
