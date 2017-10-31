/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */

package io.rsocket.util

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import io.rsocket.Payload
import io.rsocket.util.PayloadImpl.Companion.textPayload
import org.junit.Test

class PayloadImplTest {

    @Test
    fun testReuse() {
        val p = PayloadImpl(DATA_VAL, METADATA_VAL)
        assertDataAndMetadata(p, DATA_VAL, METADATA_VAL)
        assertDataAndMetadata(p, DATA_VAL, METADATA_VAL)
    }

    @Test
    fun testReuseWithExternalMark() {
        val p = PayloadImpl(DATA_VAL, METADATA_VAL)
        assertDataAndMetadata(p, DATA_VAL, METADATA_VAL)
        p.data.position(2).mark()
        assertDataAndMetadata(p, DATA_VAL, METADATA_VAL)
    }

    fun assertDataAndMetadata(p: Payload, dataVal: String, metadataVal: String?) {
        assertThat("Unexpected data.", p.dataUtf8, equalTo(dataVal))
        if (metadataVal == null) {
            assertThat("Non-null metadata", p.hasMetadata(), equalTo(false))
        } else {
            assertThat("Null metadata", p.hasMetadata(), equalTo(true))
            assertThat("Unexpected metadata.", p.metadataUtf8, equalTo(metadataVal))
        }
    }

    @Test
    fun staticMethods() {
        assertDataAndMetadata(textPayload(DATA_VAL, METADATA_VAL), DATA_VAL, METADATA_VAL)
        assertDataAndMetadata(textPayload(DATA_VAL), DATA_VAL, null)
    }

    companion object {
        val DATA_VAL = "data"
        val METADATA_VAL = "metadata"
    }
}
