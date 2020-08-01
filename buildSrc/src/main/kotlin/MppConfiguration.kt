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

import dev.whyoleg.kamp.feature.kotlin.*
import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

fun LanguageSettingsBuilder.default() {
    progressiveMode = true
    languageVersion = "1.3"
    apiVersion = "1.3"

    enableLanguageFeature("NewInference")
    enableLanguageFeature("InlineClasses")
}

inline fun Project.configureMultiplatform(crossinline block: KotlinMultiplatformExtension.() -> Unit = {}) {
    kotlinMPP {
        defaultTargets()
        kotlinOptions {
            //                    allWarningsAsErrors = true
            freeCompilerArgs += "-Xopt-in=${KotlinOptIn.requiresOptIn}"
            when (this) {
                is KotlinJvmOptions -> {
                    jvmTarget = "1.6"
                }
                is KotlinJsOptions  -> {
                    sourceMap = true
                    moduleKind = "umd"
                    metaInfo = true
                }
            }
        }
        languageSettings {
            default()
            useExperimentalAnnotation(KotlinOptIn.experimentalTime)
        }
//        sourceSets.all {
//            if (name.contains("main", true)) {
//                project.extra.set("kotlin.mpp.freeCompilerArgsForSourceSet.${name}", "-Xexplicit-api=warning")
//            }
//        }
        dependenciesMain {
            api(Dependencies.kotlin.stdlib)
            api(Dependencies.kotlinx.coroutines)
        }
        dependenciesTest {
            implementation(Dependencies.kotlin.test)
            implementation(Dependencies.kotlin.annotations.common)
            implementation(Dependencies.kotlin.annotations.junit)
            implementation(Dependencies.kotlinx.coroutines.test)
        }
        block()
    }
}

fun KotlinMultiplatformExtension.defaultTargets() {
    jvm()
    js {
        //TODO nodejs needed? is it tested?
        nodejs {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
}
