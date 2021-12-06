package io.rsocket.kotlin.samples.chat.server

import io.ktor.util.*
import io.ktor.util.collections.*

expect class Counter() {
    fun next(): Int
}

expect fun currentMillis(): Long

@OptIn(InternalAPI::class)
class Storage<T : Any> {
    private val map: MutableMap<Int, T> = ConcurrentMap()
    private val id = Counter()

    fun nextId(): Int = id.next()

    operator fun get(id: Int): T = map.getValue(id)
    fun getOrNull(id: Int): T? = map[id]

    fun save(id: Int, value: T) {
        map[id] = value
    }

    fun remove(id: Int) {
        map.remove(id)
    }

    fun contains(id: Int): Boolean = id in map
    fun values(): List<T> = map.values.toList()

}