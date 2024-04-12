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
    id("rsocketbuild.multiplatform-library")
}

description = "rsocket-kotlin Netty QUIC client/server transport implementation"

kotlin {
    jvmTarget()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.rsocketTransportNettyInternal)
            implementation(projects.rsocketInternalIo)
            api(projects.rsocketCore)
            api(libs.netty.handler)
            api(libs.netty.codec.quic)
        }
        jvmTest.dependencies {
            implementation(projects.rsocketTransportTests)
            implementation(libs.bouncycastle)
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
            //implementation("ch.qos.logback:logback-classic:1.2.11")
        }
    }
}
