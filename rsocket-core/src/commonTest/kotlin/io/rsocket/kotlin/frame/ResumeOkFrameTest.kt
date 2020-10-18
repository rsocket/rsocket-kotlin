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

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.test.*
import kotlin.test.*

class ResumeOkFrameTest : TestWithLeakCheck {

    private val lastReceivedClientPosition = 42L

    @Test
    fun testEncoding() {
        val frame = ResumeOkFrame(lastReceivedClientPosition)
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is ResumeOkFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(lastReceivedClientPosition, decodedFrame.lastReceivedClientPosition)
    }

}
