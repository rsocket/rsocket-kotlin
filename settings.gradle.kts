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

    // transports
    folder("rsocket-transports", "rsocket-transport") {
        module("local")
        module("nodejs-tcp")

        module("ktor-tcp")
        module("ktor-websocket-internal")
        module("ktor-websocket-client")
        module("ktor-websocket-server")
    }

    //deep ktor integration module
    module("rsocket-ktor", prefix = null) {
        module("rsocket-ktor-client")
        module("rsocket-ktor-server")
    }
}
