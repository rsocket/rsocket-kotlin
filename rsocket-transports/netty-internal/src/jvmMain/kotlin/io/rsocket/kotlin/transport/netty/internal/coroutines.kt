/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.transport.netty.internal

import io.netty.channel.*
import io.netty.util.concurrent.*
import io.rsocket.kotlin.internal.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("UNCHECKED_CAST")
public suspend inline fun <T> Future<T>.awaitFuture(): T = suspendCancellableCoroutine { cont ->
    addListener {
        when {
            it.isSuccess -> cont.resume(it.now as T)
            else         -> cont.resumeWithException(it.cause())
        }
    }
    cont.invokeOnCancellation {
        cancel(true)
    }
}

public suspend inline fun Future<*>.join(): Unit = suspendCancellableCoroutine { cont ->
    addListener { cont.resume(Unit) }
    cont.invokeOnCancellation { cancel(true) }
}

public suspend inline fun <reified T : Channel> ChannelFuture.awaitChannel(): T {
    awaitFuture()
    return channel() as T
}

public fun CoroutineScope.shutdownOnCancellation(vararg groups: EventLoopGroup) {
    launch(Dispatchers.Unconfined) {
        try {
            awaitCancellation()
        } finally {
            nonCancellable {
                groups.forEach { it.shutdownGracefully().join() }
            }
        }
    }
}
