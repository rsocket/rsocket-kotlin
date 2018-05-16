package io.rsocket.android.plugins

interface InterceptorOptions {

    fun connection(interceptor: DuplexConnectionInterceptor)

    fun requester(interceptor: RSocketInterceptor)

    fun handler(interceptor: RSocketInterceptor)
}