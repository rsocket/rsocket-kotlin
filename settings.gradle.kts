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
        maven("https://dl.bintray.com/whyoleg/kamp")
        mavenCentral()
        jcenter()
        gradlePluginPortal()
    }

    fun v(name: String): String = extra["kamp.version.$name"] as String

    plugins {
        val kotlinVersion = v("kotlin")

        kotlin("multiplatform") version kotlinVersion

        kotlin("plugin.allopen") version kotlinVersion

        id("kotlinx.benchmark") version v("kotlinx.benchmark")
        id("com.github.ben-manes.versions") version v("updates")
    }

}

buildscript {
    repositories {
        maven("https://dl.bintray.com/whyoleg/kamp")
        mavenCentral()
        google()
        jcenter()
    }

    fun v(name: String): String = extra["kamp.version.$name"] as String

    dependencies {
        classpath("dev.whyoleg.kamp:kamp-settings:${v("kamp")}")

        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${v("kotlin")}") //needed by atomicfu
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${v("kotlinx.atomicfu")}")
    }

}

kamp {
    versions()
    modules {
        val rsocket = "rsocket".prefixedModule

//        module("benchmarks")
        module("playground")

        rsocket("core")
        rsocket("transport-test")
        rsocket("transport-local")
        rsocket("transport-tcp")
        rsocket("transport-websocket")
        rsocket("transport-websocket-client")
        rsocket("transport-websocket-server")
    }
}
