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
                api(projects.rsocketKtor)
                api(libs.ktor.server.websockets)
            }
        }
        test {
            dependencies {
                implementation(projects.rsocketKtor.rsocketKtorClient)
                implementation(projects.rsocketTransportTests) //port provider
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
            }
        }
    }
    configureJvm()
    configureNative(NativeTargets.Nix)
}

description = "Ktor Server RSocket Plugin"
