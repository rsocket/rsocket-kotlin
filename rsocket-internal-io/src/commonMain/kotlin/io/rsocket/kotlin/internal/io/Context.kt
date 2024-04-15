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

package io.rsocket.kotlin.internal.io

import kotlinx.coroutines.*
import kotlin.coroutines.*

public fun CoroutineContext.supervisorContext(): CoroutineContext = plus(SupervisorJob(get(Job)))
public fun CoroutineContext.childContext(): CoroutineContext = plus(Job(get(Job)))

public fun <T : Job> T.onCompletion(handler: CompletionHandler): T {
    invokeOnCompletion(handler)
    return this
}

public inline fun CoroutineContext.ensureActive(onInactive: () -> Unit) {
    if (isActive) return
    onInactive() // should not throw
    ensureActive() // will throw
}

@Suppress("SuspendFunctionOnCoroutineScope")
public suspend inline fun <T> CoroutineScope.launchCoroutine(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (CancellableContinuation<T>) -> Unit,
): T = suspendCancellableCoroutine { cont ->
    val job = launch(context) { block(cont) }
    job.invokeOnCompletion { if (it != null && cont.isActive) cont.resumeWithException(it) }
    cont.invokeOnCancellation { job.cancel("launchCoroutine was cancelled", it) }
}