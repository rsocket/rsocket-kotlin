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
    ids(Plugins.atomicfu)
}

configureMultiplatform {
    val (_, js) = defaultTargets()

    dependenciesMain {
        api(Dependencies.ktor.io)
        compileOnly(Dependencies.atomicfuMetadata)
    }
    //fix ktor issue
    js!!.sourceSetMain.dependencies {
        api(npm("text-encoding"))
    }

    dependenciesTest {
        implementation(Dependencies.ktor.utils)
    }
    kampCommonTest.dependencies {
        implementation(KampModules.transportLocal)
    }
}

configurePublication()
