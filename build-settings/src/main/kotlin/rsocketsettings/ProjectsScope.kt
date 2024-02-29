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

package rsocketsettings

import org.gradle.api.initialization.*

fun Settings.projects(rootProjectName: String, block: ProjectsScope.() -> Unit) {
    rootProject.name = rootProjectName
    ProjectsScope(settings, emptyList(), emptyList()).apply(block)
}

class ProjectsScope(
    private val settings: Settings,
    private val pathParts: List<String>,
    private val prefixParts: List<String>,
) {

    fun module(name: String) {
        val moduleName = (prefixParts + name).joinToString("-")
        val modulePath = (pathParts + name).joinToString("/")

        settings.include(moduleName)
        settings.project(":$moduleName").projectDir = settings.rootDir.resolve(modulePath)
    }

    fun module(name: String, prefix: String? = name, nested: ProjectsScope.() -> Unit = {}) {
        module(name)
        folder(name, prefix, nested)
    }

    fun folder(name: String, prefix: String? = name, block: ProjectsScope.() -> Unit) {
        val prefixParts = when (prefix) {
            null -> prefixParts
            else -> prefixParts + prefix
        }
        ProjectsScope(settings, pathParts + name, prefixParts).apply(block)
    }
}
