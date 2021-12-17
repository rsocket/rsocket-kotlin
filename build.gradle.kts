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

import groovy.util.*
import org.gradle.api.publish.maven.internal.artifact.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(libs.build.kotlin)
        classpath(libs.build.kotlinx.atomicfu)
    }
}

plugins {
    alias(libs.plugins.versionUpdates)
}

subprojects {
    tasks.whenTaskAdded {
        if (name.endsWith("test", ignoreCase = true)) onlyIf { !rootProject.hasProperty("skipTests") }
        if (name.startsWith("link", ignoreCase = true)) onlyIf { !rootProject.hasProperty("skipLink") }
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        //targets configuration
        extensions.configure<KotlinMultiplatformExtension> {
            val isAutoConfigurable = project.name.startsWith("rsocket") //manual config of others
            val jvmOnly =
                project.name == "rsocket-transport-ktor-server" || //server is jvm only
                        project.name == "rsocket-test-server"
            //windows target isn't supported by ktor-network
            val supportMingw =
                project.name != "rsocket-transport-ktor" &&
                        project.name != "rsocket-transport-ktor-client"

            val jsOnly = project.name == "rsocket-transport-nodejs-tcp"
            val nodejsOnly = project.name == "rsocket-transport-nodejs-tcp"

            if (!isAutoConfigurable) return@configure

            if (!jsOnly) jvm {
                testRuns.all {
                    executionTask.configure {
                        // ActiveProcessorCount is used here, to make sure local setup is similar as on CI
                        // Github Actions linux runners have 2 cores
                        jvmArgs("-Xmx4g", "-XX:+UseParallelGC", "-XX:ActiveProcessorCount=2")
                    }
                }
            }

            if (jvmOnly) return@configure

            js {
                useCommonJs()
                //configure running tests for JS
                nodejs {
                    testTask {
                        useMocha {
                            timeout = "600s"
                        }
                    }
                }
                if (!nodejsOnly) browser {
                    testTask {
                        useKarma {
                            useConfigDirectory(rootDir.resolve("js").resolve("karma.config.d"))
                            useChromeHeadless()
                        }
                    }
                }
            }

            if (jsOnly) return@configure

            //native targets configuration
            val linuxTargets = listOf(linuxX64())
            val mingwTargets = if (supportMingw) listOf(mingwX64()) else emptyList()
            val macosTargets = listOf(macosX64(), macosArm64())
            val iosTargets = listOf(iosArm32(), iosArm64(), iosX64(), iosSimulatorArm64())
            val tvosTargets = listOf(tvosArm64(), tvosX64(), tvosSimulatorArm64())
            val watchosTargets =
                listOf(watchosArm32(), watchosArm64(), watchosX86(), watchosX64(), watchosSimulatorArm64())
            val darwinTargets = macosTargets + iosTargets + tvosTargets + watchosTargets
            val nativeTargets = darwinTargets + linuxTargets + mingwTargets

            val nativeMain by sourceSets.creating {
                dependsOn(sourceSets["commonMain"])
            }
            val nativeTest by sourceSets.creating {
                dependsOn(sourceSets["commonTest"])
            }

            nativeTargets.forEach {
                sourceSets["${it.name}Main"].dependsOn(nativeMain)
                sourceSets["${it.name}Test"].dependsOn(nativeTest)
            }

            //run tests on release + mimalloc to reduce tests execution time
            //compilation is slower in that mode, but work with buffers is much faster
            if (System.getenv("CI") == "true") {
                targets.all {
                    if (this is KotlinNativeTargetWithTests<*>) {
                        binaries.test(listOf(RELEASE))
                        testRuns.all { setExecutionSourceFrom(binaries.getTest(RELEASE)) }
                        compilations.all {
                            kotlinOptions.freeCompilerArgs += "-Xallocator=mimalloc"
                        }
                    }
                }
            }
        }

        //common configuration
        extensions.configure<KotlinMultiplatformExtension> {
            val isTestProject = project.name == "rsocket-test" || project.name == "rsocket-test-server"
            val isLibProject = project.name.startsWith("rsocket")

            sourceSets.all {
                languageSettings.apply {
                    progressiveMode = true

                    optIn("kotlin.RequiresOptIn")

                    //TODO: kludge, this is needed now, as ktor isn't fully supports kotlin 1.5.3x opt-in changes
                    // will be not needed in future with ktor 2.0.0
                    optIn("io.ktor.utils.io.core.ExperimentalIoApi")

                    if (name.contains("test", ignoreCase = true) || isTestProject) {
                        optIn("kotlin.time.ExperimentalTime")
                        optIn("kotlin.ExperimentalStdlibApi")

                        optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                        optIn("kotlinx.coroutines.InternalCoroutinesApi")
                        optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
                        optIn("kotlinx.coroutines.FlowPreview")
                        optIn("kotlinx.coroutines.DelicateCoroutinesApi")

                        optIn("io.ktor.util.InternalAPI")
                        optIn("io.ktor.utils.io.core.internal.DangerousInternalIoApi")

                        optIn("io.rsocket.kotlin.TransportApi")
                        optIn("io.rsocket.kotlin.ExperimentalMetadataApi")
                        optIn("io.rsocket.kotlin.ExperimentalStreamsApi")
                        optIn("io.rsocket.kotlin.RSocketLoggingApi")
                    }
                }
            }

            if (isLibProject && !isTestProject) {
                explicitApi()
                sourceSets["commonTest"].dependencies {
                    implementation(projects.rsocketTest)
                }
            }
        }
    }

    //workaround for https://youtrack.jetbrains.com/issue/KT-44884
    configurations.matching { !it.name.startsWith("kotlinCompilerPluginClasspath") }.all {
        resolutionStrategy.eachDependency {
            val version = requested.version
            if (requested.group == "org.jetbrains.kotlinx" &&
                requested.name.startsWith("kotlinx-coroutines") &&
                version != null && !version.contains("native-mt")
            ) {
                useVersion("$version-native-mt")
            }
        }
    }
}
