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

package io.rsocket.kotlin.operation

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*

// TODO: write better tests
class RequesterRequestResponseOperationTest : SuspendTest {
    private val deferred = CompletableDeferred<Payload>()
    private val operation = RequesterRequestResponseOperation(deferred)
    private val handler = OperationFrameHandler(operation)

    @Test
    fun testCompleteOnPayloadReceive() = test {
        handler.handleFrame(RequestFrame(FrameType.Payload, 1, false, false, true, 0, payload("hello")))
        assertTrue(deferred.isCompleted)
        assertEquals("hello", deferred.await().data.readString())
    }

    @Test
    fun testFailOnPayloadReceive() = test {
        handler.handleFrame(ErrorFrame(1, RSocketError.ApplicationError("failed")))
        assertTrue(deferred.isCompleted)
        assertFailsWith(RSocketError.ApplicationError::class, "failed") { deferred.await() }
    }

    @Test
    fun testFailOnFailure() = test {
        operation.operationFailure(IllegalStateException("failed"))
        assertTrue(deferred.isCompleted)
        assertFailsWith(IllegalStateException::class, "failed") { deferred.await() }
    }

    @Test
    fun testReassembly() = test {
        handler.handleFrame(RequestFrame(FrameType.Payload, 1, true, false, true, 0, payload("hello")))
        assertFalse(deferred.isCompleted)
        handler.handleFrame(RequestFrame(FrameType.Payload, 1, false, false, true, 0, payload(" world")))
        assertTrue(deferred.isCompleted)
        assertEquals("hello world", deferred.await().data.readString())
    }
}
