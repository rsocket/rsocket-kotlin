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
            implementation(projects.rsocketTransportLocal)
            implementation(projects.rsocketTransportKtorTcp)
            implementation(projects.rsocketTransportKtorWebsocketClient)
            implementation(projects.rsocketTransportKtorWebsocketServer)

            // ktor engines
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.client.cio)
        }
        jvmMain.dependencies {
            implementation(projects.rsocketTransportNettyTcp)
            implementation(projects.rsocketTransportNettyWebsocket)
            implementation(projects.rsocketTransportNettyQuic)
            implementation(libs.netty.codec.quic.map {
                val javaOsName = System.getProperty("os.name")
                val javaOsArch = System.getProperty("os.arch")
                val suffix = when {
                    javaOsName.contains("mac", ignoreCase = true)     -> "osx"
                    javaOsName.contains("linux", ignoreCase = true)   -> "linux"
                    javaOsName.contains("windows", ignoreCase = true) -> "windows"
                    else                                              -> error("Unknown os.name: $javaOsName")
                } + "-" + when (javaOsArch) {
                    "x86_64", "amd64"  -> "x86_64"
                    "arm64", "aarch64" -> "aarch_64"
                    else               -> error("Unknown os.arch: $javaOsArch")
                }
                "$it:$suffix"
            })
            implementation(libs.bouncycastle)
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
//            param("payloadSize", "0")
//            param("channels", "S:BUFFERED", "S:UNLIMITED", "M:BUFFERED", "M:UNLIMITED")
//            param("dispatcher", "DEFAULT", "UNCONFINED")
        }
        register("fast") {
            include("LocalRSocketKotlinBenchmark")
            param("payloadSize", "0")
            param("channels", "S:UNLIMITED")
            param("dispatcher", "UNCONFINED")
        }
        register("localPayloadSize") {
            include("LocalRSocketKotlinBenchmark")
            param("payloadSize", "0", "64")
            param("channels", "S:UNLIMITED")
            param("dispatcher", "UNCONFINED")
        }

        register("ktorTcp") {
            include("KtorTcpRSocketKotlinBenchmark")
            param("payloadSize", "0")
            param("dispatcher", "IO", "DEFAULT", "UNCONFINED")
            param("selectorDispatcher", "IO", "1", "2", "4", "8", "l1", "l2", "l4", "l8")
        }
        register("ktorTcpPayloadSize") {
            include("KtorTcpRSocketKotlinBenchmark")
            param("payloadSize", "0", "64")
        }

        register("nettyTcp") {
            include("NettyTcpRSocketKotlinBenchmark")
            param("payloadSize", "0")
            param("shareGroup", "true", "false")
        }
        register("nettyQuic") {
            include("NettyQuicRSocketKotlinBenchmark")
            param("payloadSize", "0")
        }
    }
}
