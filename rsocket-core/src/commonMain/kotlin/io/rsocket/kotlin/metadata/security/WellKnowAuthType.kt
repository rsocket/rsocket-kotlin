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

package io.rsocket.kotlin.metadata.security

public enum class WellKnowAuthType(
    public override val text: String,
    public override val identifier: Byte,
) : AuthTypeWithName, AuthTypeWithId {
    Simple("simple", 0x00),
    Bearer("bearer", 0x01);

    override fun toString(): String = text

    public companion object {
        private val byIdentifier: Array<WellKnowAuthType?> = arrayOfNulls(128)
        private val byName: MutableMap<String, WellKnowAuthType> = HashMap(128)

        init {
            values().forEach {
                byIdentifier[it.identifier.toInt()] = it
                byName[it.text] = it
            }
        }

        public operator fun invoke(identifier: Byte): WellKnowAuthType? = byIdentifier[identifier.toInt()]

        public operator fun invoke(text: String): WellKnowAuthType? = byName[text]
    }
}
