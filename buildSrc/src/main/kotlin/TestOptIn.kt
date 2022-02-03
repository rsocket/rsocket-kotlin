import org.jetbrains.kotlin.gradle.plugin.*

//TODO may be remove part of annotations
fun LanguageSettingsBuilder.optInForTest() {
    optIn("kotlin.time.ExperimentalTime")
    optIn("kotlin.ExperimentalStdlibApi")

    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    optIn("kotlinx.coroutines.InternalCoroutinesApi")
    optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
    optIn("kotlinx.coroutines.FlowPreview")
    optIn("kotlinx.coroutines.DelicateCoroutinesApi")

    optIn("io.rsocket.kotlin.TransportApi")
    optIn("io.rsocket.kotlin.ExperimentalMetadataApi")
    optIn("io.rsocket.kotlin.ExperimentalStreamsApi")
    optIn("io.rsocket.kotlin.RSocketLoggingApi")
}
