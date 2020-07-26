buildscript {
    repositories {
        maven("https://dl.bintray.com/whyoleg/kamp")
    }
    dependencies {
        classpath("dev.whyoleg.kamp:kamp-build:0.3.0.beta.1")
    }
}

plugins {
    `kotlin-dsl`
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

repositories {
    maven("https://dl.bintray.com/whyoleg/kamp")
    mavenCentral()
    jcenter()
}

kampBuild {
    publication = true
    features {
        kotlin = true
        kotlinx = true
        ktor = true
        gradle = true
        updates = true
    }
}
