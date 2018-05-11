/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.kotlin.exceptions

import io.rsocket.kotlin.Frame
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

object Exceptions {

    fun from(frame: Frame): RuntimeException {
        val message = frame.dataUtf8
        val errorCode = Frame.Error.errorCode(frame)
        return when (errorCode) {
            APPLICATION_ERROR -> ApplicationException(message)
            CANCELED -> CancelException(message)
            CONNECTION_CLOSE -> ConnectionCloseException(message)
            CONNECTION_ERROR -> ConnectionException(message)
            INVALID -> InvalidRequestException(message)
            INVALID_SETUP -> InvalidSetupException(message)
            REJECTED -> RejectedException(message)
            REJECTED_RESUME -> RejectedResumeException(message)
            REJECTED_SETUP -> RejectedSetupException(message)
            UNSUPPORTED_SETUP -> UnsupportedSetupException(message)
            else -> InvalidRequestException(
                    "Invalid Error frame: $errorCode '$message'")
        }
    }
}
