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

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*

plugins {
    id("rsocket.multiplatform")
}

kotlin {
    jvm {
        listOf(11, 17, 21).forEach { jdkVersion ->
            testRuns.create("${jdkVersion}Test") {
                executionTask.configure {
                    javaLauncher.set(
                        javaToolchains.launcherFor {
                            languageVersion.set(JavaLanguageVersion.of(jdkVersion))
                        }
                    )
                }
            }
        }
        testRuns.configureEach {
            executionTask.configure {
                // ActiveProcessorCount is used here, to make sure local setup is similar as on CI
                // GitHub Actions linux runners have 2 cores
                jvmArgs("-Xmx1g", "-XX:ActiveProcessorCount=2")
            }
        }
    }
}
