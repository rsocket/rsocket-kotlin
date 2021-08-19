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

import org.jetbrains.kotlin.gradle.plugin.mpp.*
import java.io.*
import java.net.*

plugins {
    `rsocket-build`
    `rsocket-build-jvm`
}

kotlin {
    testOptIn()
    sourceSets {
        jvmMain {
            dependencies {
                implementation(projects.rsocketTest)
                implementation(projects.rsocketTransportKtorServer)
                implementation(libs.ktor.server.cio)
            }
        }
    }
}

open class RSocketTestServer : DefaultTask() {
    @Internal
    var server: Closeable? = null
        private set

    @Internal
    lateinit var classpath: FileCollection

    @TaskAction
    fun exec() {
        try {
            println("[TestServer] start")
            val loader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getSystemClassLoader())
            server = loader.loadClass("io.rsocket.kotlin.test.server.AppKt").getMethod("start").invoke(null) as Closeable
            println("[TestServer] started")
        } catch (cause: Throwable) {
            println("[TestServer] failed: ${cause.message}")
            cause.printStackTrace()
        }
    }
}

val startTestServer by tasks.registering(RSocketTestServer::class) {
    dependsOn(tasks["jvmJar"])
    classpath = (kotlin.targets["jvm"].compilations["test"] as KotlinJvmCompilation).runtimeDependencyFiles
}

val testTasks = setOf(
    "jsLegacyNodeTest",
    "jsIrNodeTest",
    "jsLegacyBrowserTest",
    "jsIrBrowserTest",
)

rootProject.allprojects {
    if (name == "rsocket-transport-ktor") {
        tasks.matching { it.name in testTasks }.all {
            dependsOn(startTestServer)
        }
    }
}

gradle.buildFinished {
    startTestServer.get().server?.run {
        close()
        println("[TestServer] stop")
    }
}
