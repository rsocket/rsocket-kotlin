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

import dev.whyoleg.kamp.dependency.*
import dev.whyoleg.kamp.feature.kotlin.*
import dev.whyoleg.kamp.feature.kotlinx.*
import dev.whyoleg.kamp.feature.ktor.*

object Dependencies {
    val kotlin = Kotlin.dependencies(KampVersions.kotlin)
    val kotlinx = Kotlinx.dependencies(
        KotlinxVersions(
            coroutines = KampVersions.Kotlinx.coroutines,
            atomicfu = KampVersions.Kotlinx.atomicfu,
            benchmark = KampVersions.Kotlinx.benchmark
        )
    )
    val atomicfuMetadata = kotlinx.atomicfu.platforms(KampPlatform.common("native"))

    val ktor = Ktor.dependencies(KampVersions.ktor)

    val rsocketJava = RSocketJava.dependencies(KampVersions.Rsocket.java)
}

object RSocketGroup : Group by group("io.rsocket")

object RSocketJava {
    val defaultVersion = "1.0.1"

    fun dependencies(version: String = defaultVersion): RSocketJavaDependencies = RSocketJavaDependencies(version)
    val repository = RepositoryProviders.mavenCentral
}

class RSocketJavaDependencies(version: String = RSocketJava.defaultVersion) {
    private val jvm = RSocketGroup.platforms(KampPlatform.jvm).version(version)
    private fun dep(name: String) = jvm.artifact("rsocket-$name")

    val core = dep("core")
    val netty = dep("transport-netty")
    val local = dep("transport-local")
}
