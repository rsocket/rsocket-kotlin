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
    rsocket.template.transport
}

kotlin {
    configureCommon {
        main {
            dependencies {
                api(projects.rsocketTransportKtor)
                api(libs.ktor.websockets)
            }
        }
        test {
            dependencies {
                implementation(projects.rsocketTransportKtor.rsocketTransportKtorWebsocketClient)
            }
        }
    }
    configureJvm {
        test {
            dependencies {
                implementation(projects.rsocketTransportKtor.rsocketTransportKtorWebsocketServer)

                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.okhttp)

                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.jetty)
            }
        }
    }
    configureJs()
    configureNative()
}

description = "Ktor WebSocket RSocket transport implementation"

evaluationDependsOn(":rsocket-transport-tests")
