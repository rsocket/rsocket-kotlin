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

package io.rsocket.kotlin.core

import app.cash.turbine.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class ReconnectableRSocketTest : SuspendTest, TestWithLeakCheck {

    //needed for native
    private val fails = atomic(0)
    private val first = atomic(true)
    private val logger = DefaultLoggerFactory.logger("io.rsocket.kotlin.connection")

    @Test
    fun testConnectFail() = test {
        val connect: suspend () -> RSocket = { error("Failed to connect") }

        assertFailsWith(IllegalStateException::class, "Failed to connect") {
            ReconnectableRSocket(logger, connect) { cause, attempt ->
                fails.incrementAndGet()
                assertTrue(cause is IllegalStateException)
                assertEquals("Failed to connect", cause.message)
                attempt < 5
            }
        }
        assertEquals(6, fails.value)
    }

    @Test
    fun testReconnectFail() = test {
        val firstJob = Job()
        val connect: suspend () -> RSocket = {
            if (first.value) {
                first.value = false
                handler(firstJob)
            } else {
                error("Failed to connect")
            }
        }
        val rSocket = ReconnectableRSocket(logger, connect) { cause, attempt ->
            fails.incrementAndGet()
            assertTrue(cause is IllegalStateException)
            assertEquals("Failed to connect", cause.message)
            attempt < 5
        }

        assertEquals(Payload.Empty, rSocket.requestResponse(Payload.Empty))

        assertTrue(rSocket.job.isActive)
        assertEquals(0, fails.value)

        firstJob.cancelAndJoin()

        assertFailsWith(IllegalStateException::class, "Failed to connect") {
            rSocket.requestResponse(Payload.Empty)
        }

        assertFalse(rSocket.job.isActive)
        assertEquals(6, fails.value)
    }

    @Test
    fun testReconnectSuccess() = test {
        val handlerJob = Job()
        val connect: suspend () -> RSocket = {
            if (first.value) {
                first.value = false
                error("Failed to connect")
            } else {
                handler(handlerJob)
            }
        }
        val rSocket = ReconnectableRSocket(logger, connect) { cause, attempt ->
            fails.incrementAndGet()
            assertTrue(cause is IllegalStateException)
            assertEquals("Failed to connect", cause.message)
            attempt < 5
        }

        assertEquals(Payload.Empty, rSocket.requestResponse(Payload.Empty))

        assertTrue(handlerJob.isActive)
        assertTrue(rSocket.job.isActive)
        assertEquals(1, fails.value)
    }

    @Test
    fun testConnectSuccessAfterTime() = test {
        val connect: suspend () -> RSocket = {
            if (fails.value < 5) {
                delay(200)
                error("Failed to connect")
            } else {
                delay(200) //emulate connection establishment
                handler(Job())
            }
        }
        val rSocket = ReconnectableRSocket(logger, connect) { cause, attempt ->
            fails.incrementAndGet()
            assertTrue(cause is IllegalStateException)
            assertEquals("Failed to connect", cause.message)
            attempt < 5
        }

        assertEquals(Payload.Empty, rSocket.requestResponse(Payload.Empty))

        assertTrue(rSocket.job.isActive)
        assertEquals(5, fails.value)
    }

    @Test
    fun testReconnectSuccessAfterFail() = test {
        val firstJob = Job()
        val connect: suspend () -> RSocket = {
            when {
                first.value     -> {
                    first.value = false
                    handler(firstJob) //first connection
                }
                fails.value < 5 -> {
                    delay(100)
                    error("Failed to connect")
                }
                else            -> handler(Job())
            }
        }
        val rSocket = ReconnectableRSocket(logger, connect) { cause, attempt ->
            fails.incrementAndGet()
            assertTrue(cause is IllegalStateException)
            assertEquals("Failed to connect", cause.message)
            attempt < 5
        }

        assertEquals(Payload.Empty, rSocket.requestResponse(Payload.Empty))

        firstJob.cancelAndJoin()

        assertEquals(Payload.Empty, rSocket.requestResponse(Payload.Empty))

        assertTrue(rSocket.job.isActive)
        assertEquals(5, fails.value)
    }

    @Test
    fun testReconnectSuccessAfterFaiStream() = test {
        val firstJob = Job()
        val connect: suspend () -> RSocket = {
            when {
                first.value     -> {
                    first.value = false
                    handler(firstJob) //first connection
                }
                fails.value < 5 -> {
                    delay(100)
                    error("Failed to connect")
                }
                else            -> handler(Job())
            }
        }
        val rSocket = ReconnectableRSocket(logger, connect) { cause, attempt ->
            fails.incrementAndGet()
            assertTrue(cause is IllegalStateException)
            assertEquals("Failed to connect", cause.message)
            attempt < 5
        }

        launch {
            delay(200)
            firstJob.cancelAndJoin()
        }

        assertFailsWith(CancellationException::class) {
            rSocket.requestStream(Payload.Empty).collect()
        }

        rSocket.requestStream(Payload.Empty).test {
            repeat(5) {
                assertEquals(Payload.Empty, expectItem())
            }
            expectComplete()
        }

        assertTrue(rSocket.job.isActive)
        assertEquals(5, fails.value)
    }

    @Test
    fun testNoLeakMetadataPush() = testNoLeaksInteraction { metadataPush(it.data) }

    @Test
    fun testNoLeakFireAndForget() = testNoLeaksInteraction { fireAndForget(it) }

    @Test
    fun testNoLeakRequestResponse() = testNoLeaksInteraction { requestResponse(it) }

    @Test
    fun testNoLeakRequestStream() = testNoLeaksInteraction { requestStream(it).collect() }

    @Test
    fun testNoLeakRequestChannel() = testNoLeaksInteraction { requestChannel(it, emptyFlow()).collect() }

    private inline fun testNoLeaksInteraction(crossinline interaction: suspend RSocket.(payload: Payload) -> Unit) = test {
        val firstJob = Job()
        val connect: suspend () -> RSocket = {
            if (first.compareAndSet(true, false)) {
                handler(firstJob)
            } else {
                error("Failed to connect")
            }
        }
        val rSocket = ReconnectableRSocket(logger, connect) { _, attempt ->
            delay(100)
            attempt < 5
        }

        rSocket.requestResponse(Payload.Empty) //first request to be sure, that connected
        firstJob.cancelAndJoin() //cancel

        val p = payload("text")
        assertFails {
            rSocket.interaction(p) //test release on reconnecting
        }
        assertTrue(p.data.isEmpty)

        val p2 = payload("text")
        assertFails {
            rSocket.interaction(p2)  //test release on failed
        }
        assertTrue(p2.data.isEmpty)
    }

    private fun handler(job: Job): RSocket = RSocketRequestHandler(job) {
        requestResponse { payload ->
            payload
        }
        requestStream {
            flow {
                repeat(5) {
                    job.ensureActive()
                    delay(200)
                    emit(Payload.Empty)
                }
            }
        }
    }
}
