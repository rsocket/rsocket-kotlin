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

package io.rsocket.kotlin.keepalive

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KeepAliveTest : TestWithConnection() {

    private suspend fun requester(
        keepAlive: KeepAlive = KeepAlive(100.milliseconds, 1.seconds),
    ): RSocket = TestConnector {
        connectionConfig {
            this.keepAlive = keepAlive
        }
    }.connect(connection).also {
        connection.ignoreSetupFrame()
    }

    @Test
    fun requesterSendKeepAlive() = test {
        requester(KeepAlive(1.seconds, 10.seconds))
        connection.test {
            repeat(5) {
                awaitFrame { frame ->
                    assertTrue(frame is KeepAliveFrame)
                    assertTrue(frame.respond)
                }
            }
        }
    }

    @Test
    fun rSocketNotCanceledOnPresentKeepAliveTicks() = test {
        val rSocket = requester(KeepAlive(100.seconds, 100.seconds))
        connection.launch {
            repeat(50) {
                delay(100.milliseconds)
                connection.sendToReceiver(KeepAliveFrame(true, 0, Buffer()))
            }
        }
        delay(1.5.seconds)
        assertTrue(rSocket.isActive)
        connection.test {
            repeat(50) {
                awaitItem()
            }
        }
    }

    @Test
    fun requesterRespondsToKeepAlive() = test {
        requester(KeepAlive(100.seconds, 100.seconds))
        connection.launch {
            while (isActive) {
                delay(100.milliseconds)
                connection.sendToReceiver(KeepAliveFrame(true, 0, Buffer()))
            }
        }

        connection.test {
            repeat(5) {
                awaitFrame { frame ->
                    assertTrue(frame is KeepAliveFrame)
                    assertFalse(frame.respond)
                }
            }
        }
    }

    @Test
    fun noKeepAliveSentAfterRSocketCanceled() = test {
        requester().cancel()
        connection.test {
            awaitError()
        }
    }

    @Test
    fun rSocketCanceledOnMissingKeepAliveTicks() = test {
        val rSocket = requester()
        connection.test {
            while (rSocket.isActive) awaitFrame { it is KeepAliveFrame }
            awaitError()
        }
        @OptIn(InternalCoroutinesApi::class)
        assertTrue(rSocket.coroutineContext.job.getCancellationException().cause is RSocketError.ConnectionError)
    }

}
