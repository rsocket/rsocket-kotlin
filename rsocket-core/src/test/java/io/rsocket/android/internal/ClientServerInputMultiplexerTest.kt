/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package io.rsocket.android.internal

import org.junit.Assert.assertEquals

import io.rsocket.Frame
import io.rsocket.android.plugins.PluginRegistry
import io.rsocket.android.test.util.TestDuplexConnection
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Before
import org.junit.Test

class ClientServerInputMultiplexerTest {
    private var source: TestDuplexConnection? = null
    private lateinit var multiplexer: ClientServerInputMultiplexer

    @Before
    fun setup() {
        source = TestDuplexConnection()
        multiplexer = ClientServerInputMultiplexer(source!!, PluginRegistry())
    }

    @Test
    fun testSplits() {
        val clientFrames = AtomicInteger()
        val serverFrames = AtomicInteger()
        val connectionFrames = AtomicInteger()

        multiplexer
                .asClientConnection()
                .receive()
                .doOnNext { clientFrames.incrementAndGet() }
                .subscribe()
        multiplexer
                .asServerConnection()
                .receive()
                .doOnNext { serverFrames.incrementAndGet() }
                .subscribe()
        multiplexer
                .asStreamZeroConnection()
                .receive()
                .doOnNext { connectionFrames.incrementAndGet() }
                .subscribe()

        source!!.addToReceivedBuffer(Frame.Error.from(1, Exception()))
        assertEquals(1, clientFrames.get().toLong())
        assertEquals(0, serverFrames.get().toLong())
        assertEquals(0, connectionFrames.get().toLong())

        source!!.addToReceivedBuffer(Frame.Error.from(2, Exception()))
        assertEquals(1, clientFrames.get().toLong())
        assertEquals(1, serverFrames.get().toLong())
        assertEquals(0, connectionFrames.get().toLong())

        source!!.addToReceivedBuffer(Frame.Error.from(1, Exception()))
        assertEquals(2, clientFrames.get().toLong())
        assertEquals(1, serverFrames.get().toLong())
        assertEquals(0, connectionFrames.get().toLong())
    }
}

