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

class UserApiImpl(
    private val users: Users,
) : UserApi {

    override suspend fun getMe(): User {
        val userId = currentSession().userId
        return users[userId]
    }

    override suspend fun deleteMe() {
        val userId = currentSession().userId
        users -= userId
    }

    override suspend fun all(): List<User> = users.values
}

