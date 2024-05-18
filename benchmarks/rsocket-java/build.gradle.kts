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

import rsocketbuild.*

plugins {
    id("rsocketbuild.multiplatform-base")
    alias(libs.plugins.kotlin.plugin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
    jvmTarget()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.benchmarksShared)

            implementation(libs.kotlinx.coroutines.reactor)
            implementation(libs.rsocket.java.transport.local)
            implementation(libs.rsocket.java.transport.netty)
        }
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvm") {
            this as kotlinx.benchmark.gradle.JvmBenchmarkTarget
            jmhVersion = libs.versions.jmh.get()
        }
    }
    configurations {
        configureEach {
            reportFormat = "text"
        }
        named("main") {
            // all params
            param("payloadSize", "0", "64", "1024", "131072", "1048576")
        }
        register("localPayloadSize") {
            include("LocalRSocketJavaBenchmark")
            param("payloadSize", "0", "64")
        }
        register("tcpPayloadSize") {
            include("TcpRSocketJavaBenchmark")
            param("payloadSize", "0", "64")
        }
    }
}
