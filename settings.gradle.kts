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

import rsocketsettings.*

pluginManagement {
    includeBuild("build-logic")
    includeBuild("build-settings")
}

plugins {
    id("rsocketsettings.default")
}

projects("rsocket-kotlin") {
    module("rsocket-internal-io")
    module("rsocket-core")
    module("rsocket-test")

    module("rsocket-transport-tests")
    module("rsocket-transport-local")
    module("rsocket-transport-nodejs-tcp")

    //ktor transport modules
    module("rsocket-transport-ktor", prefix = null) {
        module("rsocket-transport-ktor-tcp")
        module("rsocket-transport-ktor-websocket")
        module("rsocket-transport-ktor-websocket-client")
        module("rsocket-transport-ktor-websocket-server")
    }

    //deep ktor integration module
    module("rsocket-ktor", prefix = null) {
        module("rsocket-ktor-client")
        module("rsocket-ktor-server")
    }
}
