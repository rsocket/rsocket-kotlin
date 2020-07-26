import dev.whyoleg.kamp.feature.kotlinx.*

plugins {
    id("com.github.ben-manes.versions")
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
        Kotlinx.repository(this)
    }
}
