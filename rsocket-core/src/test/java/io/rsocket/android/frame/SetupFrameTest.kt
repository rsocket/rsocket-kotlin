package io.rsocket.android.frame

import io.rsocket.android.Frame
import io.rsocket.android.Setup
import io.rsocket.android.util.PayloadImpl
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFrameTest {

    @Test
    fun setupDecode() {
        val setupFrame = Frame.Setup.from(0, 1, 100, 1000,
                "metadataMime",
                "dataMime",
                PayloadImpl.textPayload("data", "metadata"))
        val setup = Setup.create(setupFrame)
        assertEquals(setup.keepAliveInterval().millis, 100)
        assertEquals(setup.keepAliveMaxLifeTime().millis, 1000)
        assertEquals(setup.metadataMimeType(), "metadataMime")
        assertEquals(setup.dataMimeType(), "dataMime")
        assertEquals(setup.dataUtf8, "data")
        assertEquals(setup.metadataUtf8, "metadata")
        assertEquals(setupFrame.refCnt(), 0)
    }
}