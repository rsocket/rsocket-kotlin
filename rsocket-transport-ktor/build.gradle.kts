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

plugins {
    rsocket.template.library
}

kotlin {
    configureCommon {
        main {
            dependencies {
                api(projects.rsocketCore)

                api(libs.ktor.network)
                api(libs.ktor.http.cio)
            }
        }
        test {
            dependencies {
                implementation(projects.rsocketTransportKtorClient)
            }
        }
    }
    configureJvm {
        test {
            dependencies {
                implementation(projects.rsocketTransportKtorServer)

                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.okhttp)

                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.jetty)
            }
        }
    }
    configureJs()
    configureNative(NativeTargets.Nix)
}

description = "Ktor RSocket transport implementations (TCP, Websocket)"

evaluationDependsOn(":rsocket-test-server")
