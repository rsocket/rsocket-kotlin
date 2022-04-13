/*
 * Copyright 2015-2022 the original author or authors.
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

import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*

fun KotlinMultiplatformExtension.configureJvm(block: TargetBuilder.() -> Unit = {}) {
    jvmToolchain { (this as JavaToolchainSpec).setJdk(8) }
    jvm {
        listOf(11, 17).forEach { jdkVersion ->
            testRuns.create("${jdkVersion}Test") {
                executionTask.configure {
                    javaLauncher.set(
                        project.extensions.getByType<JavaToolchainService>().launcherFor { setJdk(11) }
                    )
                }
            }
        }
        testRuns.all {
            executionTask.configure {
                // ActiveProcessorCount is used here, to make sure local setup is similar as on CI
                // Github Actions linux runners have 2 cores
                jvmArgs("-Xmx4g", "-XX:ActiveProcessorCount=2")
            }
        }
    }
    TargetBuilder(sourceSets, "jvm").block()
}

private fun JavaToolchainSpec.setJdk(version: Int) {
    languageVersion.set(JavaLanguageVersion.of(version))
}