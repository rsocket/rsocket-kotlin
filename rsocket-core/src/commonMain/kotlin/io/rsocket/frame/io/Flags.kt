package io.rsocket.frame.io

object Flags {
    const val Ignore = 512
    const val Metadata = 256
    const val Follows = 128
    const val Complete = 64
    const val Next = 32
}

infix fun Int.check(flag: Int): Boolean = this and flag == flag
