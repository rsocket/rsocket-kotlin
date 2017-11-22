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
package io.rsocket.android.exceptions

import io.rsocket.android.frame.ErrorFrameFlyweight.*

import io.rsocket.android.Frame

object Exceptions {

    fun from(frame: Frame): RuntimeException {
        val errorCode = Frame.Error.errorCode(frame)

        val message = frame.dataUtf8
        when (errorCode) {
            APPLICATION_ERROR -> return ApplicationException(message)
            CANCELED -> return CancelException(message)
            CONNECTION_CLOSE -> return ConnectionCloseException(message)
            CONNECTION_ERROR -> return ConnectionException(message)
            INVALID -> return InvalidRequestException(message)
            INVALID_SETUP -> return InvalidSetupException(message)
            REJECTED -> return RejectedException(message)
            REJECTED_RESUME -> return RejectedResumeException(message)
            REJECTED_SETUP -> return RejectedSetupException(message)
            UNSUPPORTED_SETUP -> return UnsupportedSetupException(message)
            else -> return InvalidRequestException(
                    "Invalid Error frame: $errorCode '$message'")
        }
    }
}
