/*
 * Copyright 2015-2025 the original author or authors.
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
    id("rsocketbuild.multiplatform-benchmarks")
}

kotlin {
    jvmTarget()

    macosX64()
    macosArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.rsocketTransportBenchmarksBase)

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

benchmark {
    targets {
        register("jvm")
        register("macosArm64")
        register("macosX64")
        register("linuxX64")
    }

    registerTransportBenchmarks(
        "RSocketKotlin",
        listOf(
            "local",
            "ktorTcp", "ktorWs",
            "nettyTcp", "nettyQuic"
        )
    ) { transport ->
        if (transport == "local") {
            param("dispatcher", "DEFAULT", "UNCONFINED")
            param("channels", "S", "M")
        }
    }
}
