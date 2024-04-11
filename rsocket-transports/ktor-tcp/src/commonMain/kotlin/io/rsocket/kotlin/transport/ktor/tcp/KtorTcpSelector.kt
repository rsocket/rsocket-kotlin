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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.selector.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal sealed class KtorTcpSelector {
    class FromContext(val context: CoroutineContext) : KtorTcpSelector()
    class FromInstance(val selectorManager: SelectorManager, val manage: Boolean) : KtorTcpSelector()
}

internal fun KtorTcpSelector.createFor(parentContext: CoroutineContext): SelectorManager {
    val selectorManager: SelectorManager
    val manage: Boolean
    when (this) {
        is KtorTcpSelector.FromContext  -> {
            selectorManager = SelectorManager(parentContext + context)
            manage = true
        }

        is KtorTcpSelector.FromInstance -> {
            selectorManager = this.selectorManager
            manage = this.manage
        }
    }
    if (manage) Job(parentContext.job).invokeOnCompletion { selectorManager.close() }
    return selectorManager
}
