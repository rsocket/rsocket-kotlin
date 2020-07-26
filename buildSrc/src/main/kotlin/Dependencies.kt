import dev.whyoleg.kamp.dependency.*
import dev.whyoleg.kamp.feature.kotlin.*
import dev.whyoleg.kamp.feature.kotlinx.*
import dev.whyoleg.kamp.feature.ktor.*

object Dependencies {
    val kotlin = Kotlin.dependencies(KampVersions.kotlin)
    val kotlinx = Kotlinx.dependencies(
        KotlinxVersions(
            coroutines = KampVersions.Kotlinx.coroutines,
            atomicfu = KampVersions.Kotlinx.atomicfu,
            benchmark = KampVersions.Kotlinx.benchmark
        )
    )
    val atomicfuMetadata = kotlinx.atomicfu.platforms(KampPlatform.common("native"))

    val ktor = Ktor.dependencies(KampVersions.ktor)

}
