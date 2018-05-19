package io.rsocket.kotlin

import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor
import io.rsocket.kotlin.interceptors.RSocketInterceptor

interface InterceptorOptions {

    fun connection(interceptor: DuplexConnectionInterceptor)

    fun requester(interceptor: RSocketInterceptor)

    fun handler(interceptor: RSocketInterceptor)
}