import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*

fun KotlinMultiplatformExtension.configureJvm(block: TargetBuilder.() -> Unit = {}) {
    jvmToolchain { (this as JavaToolchainSpec).setJdk(8) }
    jvm {
        listOf(11, 17).forEach { jdkVersion ->
            testRuns.create("${jdkVersion}Test") {
                executionTask.configure {
                    javaLauncher.set(
                        project.extensions.getByType<JavaToolchainService>().launcherFor { setJdk(11) }
                    )
                }
            }
        }
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

private fun JavaToolchainSpec.setJdk(version: Int) {
    languageVersion.set(JavaLanguageVersion.of(version))
}