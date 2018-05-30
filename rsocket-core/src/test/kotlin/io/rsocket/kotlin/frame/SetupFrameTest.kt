package io.rsocket.kotlin.frame

import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.internal.SetupContents
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFrameTest {

    @Test
    fun setupDecode() {
        val setupFrame = Frame.Setup.from(0, 1, 100, 1000,
                "metadataMime",
                "dataMime",
                DefaultPayload.textPayload("data", "metadata"))
        val setup = SetupContents.create(setupFrame)
        assertEquals(setup.keepAliveInterval().millis, 100)
        assertEquals(setup.keepAliveMaxLifeTime().millis, 1000)
        assertEquals(setup.metadataMimeType(), "metadataMime")
        assertEquals(setup.dataMimeType(), "dataMime")
        assertEquals(setup.dataUtf8, "data")
        assertEquals(setup.metadataUtf8, "metadata")
        assertEquals(setupFrame.refCnt(), 0)
    }
}