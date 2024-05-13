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

package rsocketbuild

import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*

fun KotlinMultiplatformExtension.appleTargets() {
    macosX64()
    macosArm64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    // https://youtrack.jetbrains.com/issue/KTOR-6368, supported by kotlinx-io
    // watchosDeviceArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
}

fun KotlinMultiplatformExtension.nixTargets() {
    appleTargets()
    linuxX64()
    linuxArm64()
}

fun KotlinMultiplatformExtension.nativeTargets() {
    nixTargets()
    mingwX64()

    // not supported by ktor, supported by kotlinx-io
    // androidNativeX64()
    // androidNativeX86()
    // androidNativeArm64()
    // androidNativeArm32()
}

fun KotlinMultiplatformExtension.jsTarget(
    supportsNode: Boolean = true,
    supportsBrowser: Boolean = true,
) {
    js {
        if (supportsNode) nodejs()
        if (supportsBrowser) browser()
    }
}

fun KotlinMultiplatformExtension.jvmTarget(
    jdkVersion: Int = 8,
    jdkAdditionalTestVersions: Set<Int> = setOf(11, 17, 21),
) {
    jvmToolchain(jdkVersion)
    jvm {
        val javaToolchains = project.extensions.getByName<JavaToolchainService>("javaToolchains")
        jdkAdditionalTestVersions.forEach { jdkTestVersion ->
            testRuns.create("${jdkTestVersion}Test") {
                executionTask.configure {
                    javaLauncher.set(javaToolchains.launcherFor {
                        languageVersion.set(JavaLanguageVersion.of(jdkTestVersion))
                    })
                }
            }
        }
    }
}
