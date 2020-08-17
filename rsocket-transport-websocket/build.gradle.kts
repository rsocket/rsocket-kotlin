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
    id("maven-publish")
    id("com.jfrog.bintray")
    id("com.jfrog.artifactory")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.6"
        }
    }
    js {
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs {
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.ktor:ktor-http-cio:1.3.2-1.4.0-rc")
                api(project(":rsocket-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(project(":rsocket-transport-test"))
//                implementation("io.ktor:ktor-client-cio:1.3.2-1.4.0-rc")
//                implementation("io.ktor:ktor-server-cio:1.3.2-1.4.0-rc")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":rsocket-transport-websocket-client"))
                implementation(project(":rsocket-transport-websocket-server"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
