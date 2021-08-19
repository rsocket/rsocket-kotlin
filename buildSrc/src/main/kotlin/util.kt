import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

fun KotlinMultiplatformExtension.testOptIn() {
    sourceSets.all {
        languageSettings.testOptIn()
    }
}

fun LanguageSettingsBuilder.testOptIn() {
    useExperimentalAnnotation("kotlin.time.ExperimentalTime")
    useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")

    useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
    useExperimentalAnnotation("kotlinx.coroutines.InternalCoroutinesApi")
    useExperimentalAnnotation("kotlinx.coroutines.ObsoleteCoroutinesApi")
    useExperimentalAnnotation("kotlinx.coroutines.FlowPreview")
    useExperimentalAnnotation("kotlinx.coroutines.DelicateCoroutinesApi")

    useExperimentalAnnotation("io.ktor.util.InternalAPI")
    useExperimentalAnnotation("io.ktor.utils.io.core.internal.DangerousInternalIoApi")

    useExperimentalAnnotation("io.rsocket.kotlin.TransportApi")
    useExperimentalAnnotation("io.rsocket.kotlin.ExperimentalMetadataApi")
    useExperimentalAnnotation("io.rsocket.kotlin.ExperimentalStreamsApi")
    useExperimentalAnnotation("io.rsocket.kotlin.RSocketLoggingApi")
}

fun Project.kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure(action)
    }
}
