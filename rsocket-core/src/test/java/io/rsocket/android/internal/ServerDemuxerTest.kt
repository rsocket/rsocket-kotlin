package io.rsocket.android.internal

import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.plugins.InterceptorRegistry
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