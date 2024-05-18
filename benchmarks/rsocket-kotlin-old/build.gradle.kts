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

    // no mingw because of cio
    macosX64()
    macosArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.benchmarksShared)
            implementation("io.rsocket.kotlin:rsocket-transport-local:0.15.4")
            implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:0.15.4")
//            implementation(projects.rsocketTransportKtorWebsocketClient)
//            implementation(projects.rsocketTransportKtorWebsocketServer)

            // ktor engines
//            implementation(libs.ktor.server.cio)
//            implementation(libs.ktor.client.cio)
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
        register("macosArm64")
    }

    configurations {
        configureEach {
            reportFormat = "text"
        }
        named("main") {
            // all params, should not be called really
            include("LocalRSocketKotlinOldBenchmark")
            param("payloadSize", "0")
            param("dispatcher", "DEFAULT", "UNCONFINED")
        }
        register("fast") {
            include("LocalRSocketKotlinOldBenchmark")
            param("payloadSize", "0")
            param("dispatcher", "UNCONFINED")
        }
        register("localPayloadSize") {
            include("LocalRSocketKotlinOldBenchmark")
            param("payloadSize", "0", "64")
            param("dispatcher", "UNCONFINED")
        }
        register("ktorTcp") {
            include("KtorTcpRSocketKotlinOldBenchmark")
            param("payloadSize", "0")
        }
        register("ktorTcpPayloadSize") {
            include("KtorTcpRSocketKotlinOldBenchmark")
            param("payloadSize", "0", "64")
        }
    }
}
