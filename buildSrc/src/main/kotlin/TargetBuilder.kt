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

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

class TargetBuilder internal constructor(
    private val sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
    private val name: String
) {
    fun main(block: KotlinSourceSet.() -> Unit) {
        sourceSets.getByName("${name}Main").block()
    }

    fun test(block: KotlinSourceSet.() -> Unit) {
        sourceSets.getByName("${name}Test").block()
    }
}

fun KotlinMultiplatformExtension.configureCommon(block: TargetBuilder.() -> Unit) {
    TargetBuilder(sourceSets, "common").block()
}
