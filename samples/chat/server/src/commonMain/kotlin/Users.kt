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

package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.samples.chat.api.*

class Users {
    private val storage = Storage<User>()

    val values: List<User> get() = storage.values()

    fun getOrCreate(name: String): User =
        storage.values().find { it.name == name } ?: run {
            val userId = storage.nextId()
            User(userId, name).also { storage.save(userId, it) }
        }

    fun getOrNull(id: Int): User? = storage.getOrNull(id)

    fun delete(id: Int) {
        storage.remove(id)
    }
}

operator fun Users.get(id: Int): User = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Users.minusAssign(id: Int): Unit = delete(id)
