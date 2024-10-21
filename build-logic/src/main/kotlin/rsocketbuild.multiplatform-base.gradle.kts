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

import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.targets.js.ir.*
import org.jetbrains.kotlin.gradle.targets.jvm.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*
import org.jetbrains.kotlin.gradle.targets.native.tasks.*
import org.jetbrains.kotlin.gradle.tasks.*
import rsocketbuild.*
import rsocketbuild.tests.*

plugins {
    kotlin("multiplatform")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    compilerOptions {
        // because of INVISIBLE_REFERENCE suppression - will be removed after migration to kotlinx.io
        if (project.name != "rsocket-test") {
            allWarningsAsErrors.set(true)
        }
        progressiveMode.set(true)
        freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        optIn.addAll(OptIns.ExperimentalSubclassOptIn)
    }

    sourceSets.configureEach {
        languageSettings {
            if (name.contains("test", ignoreCase = true)) {
                // for hex API
                optIn(OptIns.ExperimentalStdlibApi)
                // for channel.isClosedForSend - need to be replaced by testing via turbine or optIn in place
                // and GlobalScope - need to be dropped
                optIn(OptIns.DelicateCoroutinesApi)

                // rsocket related
                optIn(OptIns.TransportApi)
                optIn(OptIns.RSocketTransportApi)
                optIn(OptIns.ExperimentalMetadataApi)
                optIn(OptIns.ExperimentalStreamsApi)
                optIn(OptIns.RSocketLoggingApi)
            }
        }
    }

    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    // revisit JS block after WASM support
    targets.withType<KotlinJsIrTarget>().configureEach {
        whenBrowserConfigured {
            testTask {
                useKarma {
                    useConfigDirectory(rootDir.resolve("gradle/js/karma.config.d"))
                    useChromeHeadless()
                }
            }
        }
        whenNodejsConfigured {
            testTask {
                useMocha {
                    timeout = "600s"
                }
            }
        }
    }

    //setup tests running in RELEASE mode
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.test(listOf(NativeBuildType.RELEASE))
    }
    targets.withType<KotlinNativeTargetWithTests<*>>().configureEach {
        testRuns.create("releaseTest") {
            setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE))
        }
    }
}

// for CI mainly

registerTestAggregationTask(
    name = "jvmAllTest",
    taskDependencies = { tasks.withType<KotlinJvmTest>() },
    targetFilter = { it.platformType == KotlinPlatformType.jvm }
)

registerTestAggregationTask(
    name = "nativeTest",
    taskDependencies = { tasks.withType<KotlinNativeTest>().matching { it.enabled } },
    targetFilter = { it.platformType == KotlinPlatformType.native }
)

listOf("ios", "watchos", "tvos", "macos").forEach { targetGroup ->
    registerTestAggregationTask(
        name = "${targetGroup}Test",
        taskDependencies = {
            tasks.withType<KotlinNativeTest>().matching {
                it.enabled && it.name.startsWith(targetGroup, ignoreCase = true)
            }
        },
        targetFilter = {
            it.platformType == KotlinPlatformType.native && it.name.startsWith(targetGroup, ignoreCase = true)
        }
    )
}

// on build, link even those binaries, which it's not possible to run
tasks.build {
    dependsOn(tasks.withType<KotlinNativeLink>())
}

if (providers.gradleProperty("rsocketbuild.skipTests").map(String::toBoolean).getOrElse(false)) {
    tasks.withType<AbstractTestTask>().configureEach { onlyIf { false } }
}

if (providers.gradleProperty("rsocketbuild.skipNativeLink").map(String::toBoolean).getOrElse(false)) {
    tasks.withType<KotlinNativeLink>().configureEach { onlyIf { false } }
}
