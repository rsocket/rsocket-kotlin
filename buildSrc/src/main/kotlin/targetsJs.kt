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

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import java.io.*

enum class JsTarget(
    val supportBrowser: Boolean,
    val supportNodejs: Boolean
) {
    Browser(true, false),
    Nodejs(false, true),
    Both(true, true)
}

fun KotlinMultiplatformExtension.configureJs(
    jsTarget: JsTarget = JsTarget.Both,
    block: TargetBuilder.() -> Unit = {}
) {
    js {
        useCommonJs()

        //configure running tests for JS
        if (jsTarget.supportBrowser) browser {
            testTask {
                useKarma {
                    useConfigDirectory(project.jsDir("karma.config.d"))
                    useChromeHeadless()
                }
            }
        }
        if (jsTarget.supportNodejs) nodejs {
            testTask {
                useMocha {
                    timeout = "600s"
                }
            }
        }
    }
    TargetBuilder(sourceSets, "js").block()
}

private fun Project.jsDir(folder: String): File = rootDir.resolve("gradle").resolve("js").resolve(folder)
