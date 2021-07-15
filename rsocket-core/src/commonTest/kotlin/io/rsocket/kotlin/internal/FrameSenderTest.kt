package io.rsocket.kotlin.internal

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.channels.*
import kotlin.test.*

class FrameSenderTest : SuspendTest, TestWithLeakCheck {

    private val prioritizer = Prioritizer(Channel.UNLIMITED)
    private fun sender(maxFragmentSize: Int) = FrameSender(prioritizer, InUseTrackingPool, maxFragmentSize)

    @Test
    fun testFrameFragmented() = test {
        val sender = sender(99)

        sender.sendNextPayload(1, buildPayload {
            data("1234567890".repeat(50))
        })

        repeat(6) {
            val frame = prioritizer.receive()
            assertIs<RequestFrame>(frame)
            assertTrue(frame.next)
            assertNull(frame.payload.metadata)
            if (it != 5) {
                assertTrue(frame.follows)
                assertEquals("1234567890".repeat(9), frame.payload.data.readText())
            } else { //last frame
                assertFalse(frame.follows)
                assertEquals("1234567890".repeat(5), frame.payload.data.readText())
            }
        }
    }

    @Test
    fun testFrameFragmentedFully() = test {
        val sender = sender(99)

        sender.sendNextPayload(1, buildPayload {
            data("1234567890".repeat(18))
        })

        repeat(2) {
            val frame = prioritizer.receive()
            assertIs<RequestFrame>(frame)
            assertTrue(frame.next)
            assertNull(frame.payload.metadata)
            assertEquals("1234567890".repeat(9), frame.payload.data.readText())
            if (it != 1) {
                assertTrue(frame.follows)
            } else { //last frame
                assertFalse(frame.follows)
            }
        }
    }
}
