plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(blibs.plugin.kotlin)
    implementation(blibs.plugin.kotlinx.atomicfu)
    implementation(blibs.plugin.jfrog.artifactory)
}
