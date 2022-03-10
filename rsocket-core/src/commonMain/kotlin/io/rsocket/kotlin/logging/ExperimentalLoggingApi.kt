package io.rsocket.kotlin.logging

@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is mostly internal API used for logging. This API can change in future in non backwards-compatible manner."
)
public annotation class ExperimentalLoggingApi
