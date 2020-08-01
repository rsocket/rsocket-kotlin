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

package io.rsocket.error

object ErrorCode {

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
