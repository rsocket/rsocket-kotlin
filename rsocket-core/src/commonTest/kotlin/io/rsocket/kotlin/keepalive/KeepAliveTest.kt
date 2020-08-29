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

package io.rsocket.kotlin.keepalive

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.error.*
import io.rsocket.kotlin.flow.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

class KeepAliveTest {
    private val connection = TestConnection()
    private fun requester(keepAlive: KeepAlive = KeepAlive(100.milliseconds, 1.seconds)): RSocket = run {
        val state = RSocketState(connection, keepAlive, RequestStrategy.Default, {})
        val requester = RSocketRequester(state, StreamId.client())
        state.start(RSocketRequestHandler { })
        requester
    }

    @Test
    fun requesterSendKeepAlive() = test {
        requester()
        val list = connection.sentAsFlow().take(3).toList()
        assertEquals(3, list.size)
        list.forEach {
            assertTrue(it is KeepAliveFrame)
            assertTrue(it.respond)
        }
    }

    @Test
    fun rSocketNotCanceledOnPresentKeepAliveTicks() = test {
        val rSocket = requester()
        launch(connection.job) {
            while (isActive) {
                delay(100.milliseconds)
                connection.sendToReceiver(KeepAliveFrame(true, 0, ByteReadPacket.Empty))
            }
        }
        delay(1.5.seconds)
        assertTrue(rSocket.isActive)
    }

    @Test
    fun requesterRespondsToKeepAlive() = test {
        requester(KeepAlive(100.seconds, 100.seconds))
        launch(connection.job) {
            while (isActive) {
                delay(100.milliseconds)
                connection.sendToReceiver(KeepAliveFrame(true, 0, ByteReadPacket.Empty))
            }
        }

        val list = connection.sentAsFlow().take(3).toList()
        assertEquals(3, list.size)
        list.forEach {
            assertTrue(it is KeepAliveFrame)
            assertFalse(it.respond)
        }
    }

    @Test
    fun noKeepAliveSentAfterRSocketCanceled() = test {
        requester().cancel()
        delay(500.milliseconds)
        assertEquals(0, connection.sentFrames.size)
    }

    @Test
    fun rSocketCanceledOnMissingKeepAliveTicks() = test {
        val rSocket = requester()
        delay(1.5.seconds)
        assertFalse(rSocket.isActive)
        assertTrue(rSocket.job.getCancellationException().cause is RSocketError.ConnectionError)
    }

}
