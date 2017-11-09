package io.rsocket.util

import io.rsocket.util.ExceptionUtil.noStacktrace
import org.junit.Assert.assertEquals

import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test

class ExceptionUtilTest {
    @Test
    fun testNoStacktrace() {
        val ex = noStacktrace(RuntimeException("RE"))
        assertEquals(
                String.format(
                        "java.lang.RuntimeException: RE%n" + "\tat java.lang.RuntimeException.<init>(Unknown Source)%n"),
                stacktraceString(ex))
    }

    private fun stacktraceString(ex: RuntimeException): String {
        val stringWriter = StringWriter()
        ex.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
