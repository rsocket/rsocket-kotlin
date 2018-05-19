package io.rsocket.kotlin.internal

internal object ExceptionUtil {
    fun <T : Throwable> noStacktrace(ex: T): T {
        ex.stackTrace = arrayOf(StackTraceElement(
                ex.javaClass.name,
                "<init>",
                null,
                -1))
        return ex
    }
}
