pluginManagement {
    repositories {
        maven("https://dl.bintray.com/kotlin/kotlinx")
        maven("https://dl.bintray.com/whyoleg/kamp")
        mavenCentral()
        jcenter()
        gradlePluginPortal()
    }

    fun v(name: String): String = extra["kamp.version.$name"] as String

    plugins {
        val kotlinVersion = v("kotlin")

        kotlin("multiplatform") version kotlinVersion

        kotlin("plugin.allopen") version kotlinVersion

        id("kotlinx.benchmark") version v("kotlinx.benchmark")
        id("com.github.ben-manes.versions") version v("updates")
    }

}

buildscript {
    repositories {
        maven("https://dl.bintray.com/whyoleg/kamp")
        mavenCentral()
        google()
        jcenter()
    }

    fun v(name: String): String = extra["kamp.version.$name"] as String

    dependencies {
        classpath("dev.whyoleg.kamp:kamp-settings:${v("kamp")}")

        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${v("kotlin")}") //needed by atomicfu
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${v("kotlinx.atomicfu")}")
    }

}

kamp {
    versions()
    modules {
        val rsocket = "rsocket".prefixedModule

//        module("benchmarks")
        module("playground")

        rsocket("core")
        rsocket("transport-test")
        rsocket("transport-local")
        rsocket("transport-tcp")
        rsocket("transport-websocket")
        rsocket("transport-websocket-client")
        rsocket("transport-websocket-server")
    }
}
