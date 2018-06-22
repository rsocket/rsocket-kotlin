package io.rsocket.kotlin

import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor
import io.rsocket.kotlin.interceptors.RSocketInterceptor

/**
 * Configures RSocket interceptors
 */
interface InterceptorOptions {

    /**
     * @param interceptor adds [DuplexConnectionInterceptor]
     */
    fun connection(interceptor: DuplexConnectionInterceptor)

    /**
     * @param interceptor adds [RSocketInterceptor] for requester [RSocket]
     */
    fun requester(interceptor: RSocketInterceptor)

    /**
     * @param interceptor adds [RSocketInterceptor] for handler (responder) [RSocket]
     */
    fun handler(interceptor: RSocketInterceptor)
}