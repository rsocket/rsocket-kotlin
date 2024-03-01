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

package rsocketbuild.tests

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

fun Project.registerTestAggregationTask(
    name: String,
    taskDependencies: () -> TaskCollection<*>,
    targetFilter: (KotlinTarget) -> Boolean,
) {
    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        var called = false
        targets.matching(targetFilter).configureEach {
            if (called) return@configureEach
            called = true

            tasks.register(name) {
                group = "verification"
                dependsOn(taskDependencies())
            }
        }
    }
}
