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

package io.rsocket.kotlin.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.exceptions.*
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.APPLICATION_ERROR
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.CANCELED
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.CONNECTION_CLOSE
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.CONNECTION_ERROR
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.INVALID
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.INVALID_SETUP
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.REJECTED
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.REJECTED_RESUME
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.REJECTED_SETUP
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight.UNSUPPORTED_SETUP
import java.nio.charset.StandardCharsets
import org.junit.Test

class ErrorFrameFlyweightTest {
    private val byteBuf = Unpooled.buffer(1024)

    @Test
    fun testEncoding() {
        val encoded = ErrorFrameFlyweight.encode(
                byteBuf,
                1,
                ErrorFrameFlyweight.APPLICATION_ERROR,
                Unpooled.copiedBuffer("d", StandardCharsets.UTF_8))
        assertEquals("00000b000000012c000000020164", ByteBufUtil.hexDump(byteBuf, 0, encoded))

        assertEquals(ErrorFrameFlyweight.APPLICATION_ERROR.toLong(), ErrorFrameFlyweight.errorCode(byteBuf).toLong())
        assertEquals("d", ErrorFrameFlyweight.message(byteBuf))
    }

    @Test
    @Throws(Exception::class)
    fun testExceptions() {
        assertExceptionMapping(INVALID_SETUP, InvalidSetupException::class.java)
        assertExceptionMapping(UNSUPPORTED_SETUP, UnsupportedSetupException::class.java)
        assertExceptionMapping(REJECTED_SETUP, RejectedSetupException::class.java)
        assertExceptionMapping(REJECTED_RESUME, RejectedResumeException::class.java)
        assertExceptionMapping(CONNECTION_ERROR, ConnectionException::class.java)
        assertExceptionMapping(CONNECTION_CLOSE, ConnectionCloseException::class.java)
        assertExceptionMapping(APPLICATION_ERROR, ApplicationException::class.java)
        assertExceptionMapping(REJECTED, RejectedException::class.java)
        assertExceptionMapping(CANCELED, CancelException::class.java)
        assertExceptionMapping(INVALID, InvalidRequestException::class.java)
    }

    @Throws(Exception::class)
    private fun <T : Exception> assertExceptionMapping(errorCode: Int, exceptionClass: Class<T>) {
        val ex = exceptionClass.getConstructor(String::class.java).newInstance("error data")
        val f = Frame.Error.from(0, ex)

        assertEquals(errorCode.toLong(), Frame.Error.errorCode(f).toLong())

        val ex2 = Exceptions.from(f)

        assertEquals(ex.message, ex2.message)
        assertTrue(exceptionClass.isInstance(ex2))
    }
}
