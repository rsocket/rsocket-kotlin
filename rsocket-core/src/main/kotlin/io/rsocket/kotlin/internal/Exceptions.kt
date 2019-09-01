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

import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.exceptions.*
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight

internal object Exceptions {

    /**
     * Creates [RuntimeException] from given Error [Frame]
     */
    fun from(frame: Frame): RuntimeException {
        val message = frame.dataUtf8
        val errorCode = Frame.Error.errorCode(frame)
        return when (errorCode) {
            ErrorFrameFlyweight.APPLICATION_ERROR -> ApplicationException(message)
            ErrorFrameFlyweight.CANCELED -> CancelException(message)
            ErrorFrameFlyweight.CONNECTION_CLOSE -> ConnectionCloseException(message)
            ErrorFrameFlyweight.CONNECTION_ERROR -> ConnectionException(message)
            ErrorFrameFlyweight.INVALID -> InvalidRequestException(message)
            ErrorFrameFlyweight.INVALID_SETUP -> InvalidSetupException(message)
            ErrorFrameFlyweight.REJECTED -> RejectedException(message)
            ErrorFrameFlyweight.REJECTED_RESUME -> RejectedResumeException(message)
            ErrorFrameFlyweight.REJECTED_SETUP -> RejectedSetupException(message)
            ErrorFrameFlyweight.UNSUPPORTED_SETUP -> UnsupportedSetupException(message)
            else -> InvalidRequestException(
                    "Invalid Error frame: $errorCode '$message'")
        }
    }

}
