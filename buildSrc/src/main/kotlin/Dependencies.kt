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

}
