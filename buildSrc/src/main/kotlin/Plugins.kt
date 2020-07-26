import dev.whyoleg.kamp.feature.gradle.*
import dev.whyoleg.kamp.feature.kotlin.*
import dev.whyoleg.kamp.feature.kotlinx.*

object Plugins {
    val mpp = listOf(KotlinPlugins.multiplatform, GradlePlugins.mavenPublish)
    val mppWithAtomic = mpp + KotlinxPlugins.atomicfu
    val benchmarks = mpp + KotlinxPlugins.benchmark + KotlinPlugins.allOpen
}
