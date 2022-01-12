import org.jetbrains.kotlin.gradle.dsl.*

fun KotlinMultiplatformExtension.configureJvm(block: TargetBuilder.() -> Unit = {}) {
    jvm {
        testRuns.all {
            executionTask.configure {
                // ActiveProcessorCount is used here, to make sure local setup is similar as on CI
                // Github Actions linux runners have 2 cores
                jvmArgs("-Xmx4g", "-XX:ActiveProcessorCount=2")
            }
        }
    }
    TargetBuilder(sourceSets, "jvm").block()
}
