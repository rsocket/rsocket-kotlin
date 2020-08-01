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
    ids(Plugins.mpp)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.client.engines.cio) //jvm engine
        api(Dependencies.ktor.client.engines.js) //js engine

        api(Dependencies.ktor.server.engines.cio) //jvm engine
    }
    kampCommonMain.dependencies {
        api(KampModules.transportLocal)
        api(KampModules.transportTcp)
        api(KampModules.transportWebsocketClient)
        api(KampModules.transportWebsocketServer)
    }
    //fix ktor issue
    js().sourceSetMain.dependencies {
        api(npm("utf-8-validate"))
        api(npm("abort-controller"))
        api(npm("bufferutil"))
        api(npm("fs"))
    }
}
