import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

class TargetBuilder internal constructor(
    private val sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
    private val name: String
) {
    fun main(block: KotlinSourceSet.() -> Unit) {
        sourceSets.getByName("${name}Main").block()
    }

    fun test(block: KotlinSourceSet.() -> Unit) {
        sourceSets.getByName("${name}Test").block()
    }
}

fun KotlinMultiplatformExtension.configureCommon(block: TargetBuilder.() -> Unit) {
    TargetBuilder(sourceSets, "common").block()
}
