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

import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import org.junit.Assert
import org.junit.Test

internal class ServerDemuxerTest : ConnectionDemuxerTest() {

    override fun createDemuxer(conn: DuplexConnection,
                               interceptorRegistry: InterceptorRegistry): ConnectionDemuxer =
            ServerConnectionDemuxer(conn, interceptorRegistry)

    @Test
    override fun requester() {
        val frame = Frame.Error.from(2, RuntimeException())
        source.addToReceivedBuffer(frame)

        Assert.assertEquals(1, requesterFrames.get())
        Assert.assertEquals(0, responderFrames.get())
        Assert.assertEquals(0, setupFrames.get())
        Assert.assertEquals(0, serviceFrames.get())
    }

    @Test
    override fun responder() {
        val frame = Frame.Error.from(1, RuntimeException())
        source.addToReceivedBuffer(frame)

        Assert.assertEquals(0, requesterFrames.get())
        Assert.assertEquals(1, responderFrames.get())
        Assert.assertEquals(0, setupFrames.get())
        Assert.assertEquals(0, serviceFrames.get())
    }
}