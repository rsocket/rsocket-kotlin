import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import java.io.*
import java.net.*

open class RSocketTestServer : DefaultTask() {
    @Internal
    var server: Closeable? = null
        private set

    @Internal
    lateinit var classpath: FileCollection

    @TaskAction
    fun exec() {
        try {
            println("[TestServer] start")
            val loader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getSystemClassLoader())
            server = loader.loadClass("io.rsocket.kotlin.test.server.AppKt").getMethod("start").invoke(null) as Closeable
            println("[TestServer] started")
        } catch (cause: Throwable) {
            println("[TestServer] failed: ${cause.message}")
            cause.printStackTrace()
        }
    }
}

fun Project.registerTestServer() {
    val startTestServer by tasks.registering(RSocketTestServer::class) {
        dependsOn(tasks["jvmJar"])
        val target = this@registerTestServer.extensions.getByType<KotlinMultiplatformExtension>().targets["jvm"]
        classpath = (target.compilations["test"] as KotlinJvmCompilation).runtimeDependencyFiles
    }

    gradle.buildFinished {
        startTestServer.get().server?.run {
            close()
            println("[TestServer] stop")
        }
    }
}

private val testTasks = setOf(
    "jsLegacyNodeTest",
    "jsIrNodeTest",
    "jsLegacyBrowserTest",
    "jsIrBrowserTest",
)

fun Project.dependsOnTestServer() {
    val testServer = evaluationDependsOn(":rsocket-test-server")
    val startTestServer by testServer.tasks.getting

    tasks.matching { it.name in testTasks }.all {
        dependsOn(startTestServer)
    }
}
