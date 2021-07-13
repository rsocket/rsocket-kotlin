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

package io.rsocket.kotlin.internal.handler

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*

internal actual abstract class ResponderFrameHandler actual constructor(pool: ObjectPool<ChunkBuffer>) : BaseResponderFrameHandler(pool) {
    actual override var job: Job? = null
    actual override var hasMetadata: Boolean = false
}

internal actual abstract class RequesterFrameHandler actual constructor(pool: ObjectPool<ChunkBuffer>) : BaseRequesterFrameHandler(pool) {
    actual override var hasMetadata: Boolean = false
}
