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

