import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.yarn.*
import java.io.*

enum class JsTarget(
    val supportBrowser: Boolean,
    val supportNodejs: Boolean
) {
    Browser(true, false),
    Nodejs(false, true),
    Both(true, true)
}

fun KotlinMultiplatformExtension.configureJs(
    jsTarget: JsTarget = JsTarget.Both,
    block: TargetBuilder.() -> Unit = {}
) {
    js {
        useCommonJs()

        //configure running tests for JS
        if (jsTarget.supportBrowser) browser {
            testTask {
                useKarma {
                    useConfigDirectory(project.jsDir("karma.config.d"))
                    useChromeHeadless()
                }
            }
        }
        if (jsTarget.supportNodejs) nodejs {
            testTask {
                useMocha {
                    timeout = "600s"
                }
            }
        }
    }
    TargetBuilder(sourceSets, "js").block()
}

//should be called only in root project
fun Project.configureYarn() {
    yarn.lockFileDirectory = project.jsDir("yarn")
}

private fun Project.jsDir(folder: String): File = rootDir.resolve("gradle").resolve("js").resolve(folder)
