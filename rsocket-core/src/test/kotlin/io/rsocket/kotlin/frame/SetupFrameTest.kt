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
                DefaultPayload.text("data", "metadata"))
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