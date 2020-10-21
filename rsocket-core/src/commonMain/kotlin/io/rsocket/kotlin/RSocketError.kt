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

package io.rsocket.kotlin

sealed class RSocketError(internal val errorCode: Int, message: String) : Throwable(message) {

    sealed class Setup(errorCode: Int, message: String) : RSocketError(errorCode, message) {
        class Invalid(message: String) : Setup(ErrorCode.InvalidSetup, message)
        class Unsupported(message: String) : Setup(ErrorCode.UnsupportedSetup, message)
        class Rejected(message: String) : Setup(ErrorCode.RejectedSetup, message)
    }

    class RejectedResume(message: String) : RSocketError(ErrorCode.RejectedResume, message)

    class ConnectionError(message: String) : RSocketError(ErrorCode.ConnectionError, message)
    class ConnectionClose(message: String) : RSocketError(ErrorCode.ConnectionClose, message)

    class ApplicationError(message: String) : RSocketError(ErrorCode.ApplicationError, message)
    class Rejected(message: String) : RSocketError(ErrorCode.Rejected, message)
    class Canceled(message: String) : RSocketError(ErrorCode.Canceled, message)
    class Invalid(message: String) : RSocketError(ErrorCode.Invalid, message)

    class Custom(errorCode: Int, message: String) : RSocketError(errorCode, message) {
        init {
            require(errorCode >= ErrorCode.CustomMin || errorCode <= ErrorCode.CustomMax) {
                "Allowed errorCode value should be in range [0x00000301-0xFFFFFFFE]"
            }
        }
    }
}

internal fun RSocketError(streamId: Int, errorCode: Int, message: String): Throwable =
    when (streamId) {
        0 -> when (errorCode) {
            ErrorCode.InvalidSetup     -> RSocketError.Setup.Invalid(message)
            ErrorCode.UnsupportedSetup -> RSocketError.Setup.Unsupported(message)
            ErrorCode.RejectedSetup    -> RSocketError.Setup.Rejected(message)
            ErrorCode.RejectedResume   -> RSocketError.RejectedResume(message)
            ErrorCode.ConnectionError  -> RSocketError.ConnectionError(message)
            ErrorCode.ConnectionClose  -> RSocketError.ConnectionClose(message)
            else                       -> IllegalArgumentException("Invalid Error frame in Stream ID 0: $errorCode '%$message'")
        }
        else -> when (errorCode) {
            ErrorCode.ApplicationError -> RSocketError.ApplicationError(message)
            ErrorCode.Rejected -> RSocketError.Rejected(message)
            ErrorCode.Canceled -> RSocketError.Canceled(message)
            ErrorCode.Invalid -> RSocketError.Invalid(message)
            else                       -> when (errorCode >= ErrorCode.CustomMin || errorCode <= ErrorCode.CustomMax) {
                true -> RSocketError.Custom(errorCode, message)
                false -> IllegalArgumentException("Invalid Error frame in Stream ID $streamId: $errorCode '$message'")
            }
        }
    }

internal object ErrorCode {

    //stream id = 0
    const val InvalidSetup: Int = 0x00000001
    const val UnsupportedSetup: Int = 0x00000002
    const val RejectedSetup: Int = 0x00000003
    const val RejectedResume: Int = 0x00000004

    const val ConnectionError: Int = 0x00000101
    const val ConnectionClose: Int = 0x00000102

    //stream id != 0
    const val ApplicationError: Int = 0x00000201
    const val Rejected: Int = 0x00000202
    const val Canceled: Int = 0x00000203
    const val Invalid: Int = 0x00000204

    //reserved
    const val Reserved: Int = 0x00000000
    const val ReservedForExtension: Int = 0xFFFFFFFF.toInt()

    //custom error codes range
    const val CustomMin: Int = 0x00000301
    const val CustomMax: Int = 0xFFFFFFFE.toInt()
}
