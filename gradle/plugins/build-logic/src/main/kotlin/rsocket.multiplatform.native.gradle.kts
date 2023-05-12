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

import org.jetbrains.kotlin.gradle.plugin.mpp.*

plugins {
    id("rsocket.multiplatform")
}

kotlin {
    targets.configureEach {
        //add another test task with release binary
        if (this is KotlinNativeTargetWithTests<*>) {
            binaries.test(listOf(NativeBuildType.RELEASE))
            testRuns.create("releaseTest") {
                setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE))
            }
        }
    }
}
