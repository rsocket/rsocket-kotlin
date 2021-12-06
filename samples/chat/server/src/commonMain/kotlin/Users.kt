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
