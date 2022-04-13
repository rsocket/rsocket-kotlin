/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.core

import io.rsocket.kotlin.frame.io.*

public sealed interface MimeType

public sealed interface MimeTypeWithName : MimeType {
    public val text: String
}

public sealed interface MimeTypeWithId : MimeType {
    public val identifier: Byte
}

public data class CustomMimeType(override val text: String) : MimeTypeWithName {
    init {
        text.requireAscii()
        require(text.length in 1..128) { "Mime-type text length must be in range 1..128 but was '${text.length}'" }
    }

    override fun toString(): String = text
}

public data class ReservedMimeType(override val identifier: Byte) : MimeTypeWithId {
    init {
        require(identifier in 1..128) { "Mime-type identifier must be in range 1..128 but was '${identifier}'" }
    }

    override fun toString(): String = "ID: $identifier"
}
