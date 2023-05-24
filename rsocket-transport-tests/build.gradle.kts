/*
 * Copyright 2015-2023 the original author or authors.
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
    id("rsocket.template.test")
    id("rsocket.target.all")
    id("kotlinx-atomicfu")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.rsocketTest)
            }
        }
        jvmTest {
            dependencies {
                implementation(projects.rsocketTransportKtor.rsocketTransportKtorTcp)
                implementation(projects.rsocketTransportKtor.rsocketTransportKtorWebsocketServer)
                implementation(libs.ktor.server.cio)
            }
        }
    }
}

//open class StartTransportTestServer : DefaultTask() {
//    @Internal
//    var server: Closeable? = null
//        private set
//
//    @Internal
//    lateinit var classpath: FileCollection
//
//    @TaskAction
//    fun exec() {
//        try {
//            println("[TransportTestServer] start")
//            server = URLClassLoader(
//                classpath.map { it.toURI().toURL() }.toTypedArray(),
//                ClassLoader.getSystemClassLoader()
//            )
//                .loadClass("io.rsocket.kotlin.transport.tests.server.AppKt")
//                .getMethod("start")
//                .invoke(null) as Closeable
//            println("[TransportTestServer] started")
//        } catch (cause: Throwable) {
//            println("[TransportTestServer] failed to start: ${cause.message}")
//            cause.printStackTrace()
//        }
//    }
//}
//
//val startTransportTestServer by tasks.registering(StartTransportTestServer::class) {
//    dependsOn(tasks["jvmTest"]) //TODO?
//    classpath = (kotlin.targets["jvm"].compilations["test"] as KotlinJvmCompilation).runtimeDependencyFiles
//}
//
//rootProject.allprojects {
//    if (name == "rsocket-transport-ktor-websocket-client") {
//        val names = setOf(
//            "jsLegacyNodeTest",
//            "jsIrNodeTest",
//            "jsLegacyBrowserTest",
//            "jsIrBrowserTest",
//        )
//        tasks.all {
//            if (name in names) dependsOn(startTransportTestServer)
//        }
//    }
//}
//
//gradle.buildFinished {
//    startTransportTestServer.get().server?.run {
//        close()
//        println("[TransportTestServer] stopped")
//    }
//}
