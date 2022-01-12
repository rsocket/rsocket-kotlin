import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*

fun Project.kotlin(configure: Action<KotlinMultiplatformExtension>): Unit = extensions.configure("kotlin", configure)
