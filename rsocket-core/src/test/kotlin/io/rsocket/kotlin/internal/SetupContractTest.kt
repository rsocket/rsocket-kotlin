/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal

import io.reactivex.processors.ReplayProcessor
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.exceptions.InvalidSetupException
import io.rsocket.kotlin.exceptions.RejectedSetupException
import io.rsocket.kotlin.internal.frame.SetupFrameFlyweight
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import io.rsocket.kotlin.DefaultPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SetupContractTest {
    lateinit var sender: ReplayProcessor<Frame>
    lateinit var receiver: ReplayProcessor<Frame>
    lateinit var connection: LocalDuplexConnection

    @Before
    fun setUp() {
        sender = ReplayProcessor.create<Frame>()
        receiver = ReplayProcessor.create<Frame>()
        connection = LocalDuplexConnection("test", sender, receiver)
    }

    @Test
    fun setupVersionMismatch() {
        val errs = Errors()
        val version = 2
        val setupContract = SetupContract(
                connection,
                errs,
                version,
                leaseEnabled = false,
                resumeEnabled = false)
        val frame = Frame.Setup.from(
                0,
                1,
                0,
                0,
                "md",
                "d",
                DefaultPayload.EMPTY)
        val subs = TestSubscriber<Frame>()
        receiver.onNext(frame)
        setupContract.receive().subscribe(subs)
        val closed = setupContract.onClose().blockingAwait(1, TimeUnit.SECONDS)
        if (!closed) {
            throw IllegalStateException("Connection did not close")
        }
        val sent = sender.values
        assertEquals(1, sent.size)
        val sentFrame = sent.first() as Frame
        assertTrue(sentFrame.type == FrameType.ERROR)
        val actualError = Exceptions.from(sentFrame)
        assertTrue(actualError is InvalidSetupException)
        assertTrue(actualError.message != null)
        assertTrue(actualError.message!!.startsWith("Unsupported protocol"))
        assertEquals(0, subs.valueCount())
        assertEquals(0, frame.refCnt())
        assertTrue(errs.isEmpty())
    }

    @Test
    fun setupVersionMatch() {
        val errs = Errors()
        val version = 1
        val setupContract = SetupContract(
                connection,
                errs,
                version,
                leaseEnabled = false,
                resumeEnabled = false)
        val frame = Frame.Setup.from(
                0,
                1,
                0,
                0,
                "md",
                "d",
                DefaultPayload.EMPTY)
        val subs = TestSubscriber<Frame>()
        receiver.onNext(frame)
        setupContract.receive().subscribe(subs)
        val closed = setupContract.onClose().blockingAwait(1, TimeUnit.SECONDS)
        if (closed) {
            throw IllegalStateException("Connection did close unexpectedly")
        }
        val sent = sender.values
        assertEquals(0, sent.size)
        assertEquals(1, subs.valueCount())
        assertTrue(frame.refCnt() > 0)
        assertTrue(errs.isEmpty())
    }

    @Test
    fun setupLeaseNotSupported() {
        val errs = Errors()
        val version = 1
        val setupContract = SetupContract(
                connection,
                errs,
                version,
                leaseEnabled = false,
                resumeEnabled = false)

        val frame = Frame.Setup.from(
                SetupFrameFlyweight.FLAGS_WILL_HONOR_LEASE,
                1,
                0,
                0,
                "md",
                "d",
                DefaultPayload.EMPTY)
        val subs = TestSubscriber<Frame>()
        receiver.onNext(frame)
        setupContract.receive().subscribe(subs)
        val closed = setupContract.onClose().blockingAwait(1, TimeUnit.SECONDS)
        if (!closed) {
            throw IllegalStateException("Connection did not close")
        }
        val sent = sender.values
        assertEquals(1, sent.size)
        val sentFrame = sent.first() as Frame
        assertTrue(sentFrame.type == FrameType.ERROR)
        val actualError = Exceptions.from(sentFrame)
        assertTrue(actualError is RejectedSetupException)
        assertEquals("Lease is not supported", actualError.message)
        assertEquals(0, subs.valueCount())
        assertEquals(0, frame.refCnt())
        assertTrue(errs.isEmpty())
    }

    @Test
    fun setupResumeNotSupported() {
        val errs = Errors()
        val version = 1
        val setupContract = SetupContract(
                connection,
                errs,
                version,
                leaseEnabled = false,
                resumeEnabled = false)

        val frame = Frame.Setup.from(
                SetupFrameFlyweight.FLAGS_RESUME_ENABLE,
                1,
                0,
                0,
                "md",
                "d",
                DefaultPayload.EMPTY)
        val subs = TestSubscriber<Frame>()
        receiver.onNext(frame)
        setupContract.receive().subscribe(subs)
        val closed = setupContract.onClose().blockingAwait(1, TimeUnit.SECONDS)
        if (!closed) {
            throw IllegalStateException("Connection did not close")
        }
        val sent = sender.values
        assertEquals(1, sent.size)
        val sentFrame = sent.first() as Frame
        assertTrue(sentFrame.type == FrameType.ERROR)
        val actualError = Exceptions.from(sentFrame)
        assertTrue(actualError is RejectedSetupException)
        assertEquals("Resumption is not supported", actualError.message)
        assertEquals(0, subs.valueCount())
        assertEquals(0, frame.refCnt())
        assertTrue(errs.isEmpty())
    }

    @Test
    fun unknownFrame() {
        val errs = Errors()
        val version = 1
        val setupContract = SetupContract(
                connection,
                errs,
                version,
                leaseEnabled = false,
                resumeEnabled = false)
        val frame = Frame.Error.from(0, RuntimeException())
        val subs = TestSubscriber<Frame>()
        receiver.onNext(frame)
        setupContract.receive().subscribe(subs)
        val closed = setupContract.onClose().blockingAwait(1, TimeUnit.SECONDS)
        if (closed) {
            throw IllegalStateException("Connection did close unexpectedly")
        }
        val sent = sender.values
        assertEquals(0, sent.size)
        assertEquals(0, subs.valueCount())
        assertTrue(frame.refCnt() == 0)
        assertTrue(!errs.isEmpty())
    }

    class Errors : (Throwable) -> Unit {
        private val errors = ArrayList<Throwable>()
        override fun invoke(err: Throwable) {
            errors += err
        }

        fun get() = errors

        fun isEmpty() = errors.isEmpty()
    }
}