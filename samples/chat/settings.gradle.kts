pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    val kotlinVersion: String by settings
    plugins {
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "chat"

include("api")
include("client")
include("server")
