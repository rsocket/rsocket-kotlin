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

@file:Suppress("UnstableApiUsage")

val kotlinVersionOverride = providers.gradleProperty("rsocketbuild.kotlinVersionOverride").orNull?.takeIf(String::isNotBlank)

if (kotlinVersionOverride != null) {
    val kotlinDevRepository = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev"
    val kotlinGroup = "org.jetbrains.kotlin"

    logger.lifecycle("Kotlin version override: $kotlinVersionOverride, repository: $kotlinDevRepository")

    pluginManagement {
        repositories {
            maven(kotlinDevRepository) {
                content { includeGroupAndSubgroups(kotlinGroup) }
            }
        }
    }

    dependencyResolutionManagement {
        repositories {
            maven(kotlinDevRepository) {
                content { includeGroupAndSubgroups(kotlinGroup) }
            }
        }

        versionCatalogs {
            named("libs") {
                version("kotlin", kotlinVersionOverride)
            }
        }
    }
}
