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

import kotlinx.atomicfu.*
import java.util.concurrent.*

class Users {
    private val users: MutableMap<Int, User> = ConcurrentHashMap()
    private val usersId = atomic(0)

    val values: List<User> get() = users.values.toList()

    fun getOrCreate(name: String): User =
        users.values.find { it.name == name } ?: run {
            val userId = usersId.incrementAndGet()
            User(userId, name).also { users[userId] = it }
        }

    fun getOrNull(id: Int): User? = users[id]

    fun delete(id: Int) {
        users -= id
    }
}

operator fun Users.get(id: Int): User = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Users.minusAssign(id: Int): Unit = delete(id)
