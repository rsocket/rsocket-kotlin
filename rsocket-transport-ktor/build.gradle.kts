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
    kotlin("multiplatform")

    id("maven-publish")
    id("com.jfrog.bintray")
    id("com.jfrog.artifactory")
}

val ktorVersion: String by rootProject

kotlin {
    jvm()
    js()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.ktor:ktor-network:$ktorVersion")
                api("io.ktor:ktor-http-cio:$ktorVersion")
                api(project(":rsocket-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":rsocket-transport-ktor-client"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")

                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("io.ktor:ktor-server-jetty:$ktorVersion")
                implementation("io.ktor:ktor-server-tomcat:$ktorVersion")

                implementation(project(":rsocket-transport-ktor-server"))
            }
        }
    }
}
