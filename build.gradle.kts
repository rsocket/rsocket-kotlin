/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.jfrog.bintray.gradle.*
import com.jfrog.bintray.gradle.tasks.*
import org.gradle.api.publish.maven.internal.artifact.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.*
import org.jetbrains.kotlin.gradle.targets.jvm.*
import org.jfrog.gradle.plugin.artifactory.dsl.*

buildscript {
    repositories {
        mavenCentral()
    }
    val kotlinVersion: String by rootProject
    val kotlinxAtomicfuVersion: String by rootProject

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$kotlinxAtomicfuVersion")
    }
}

val kotlinxAtomicfuVersion: String by rootProject

plugins {
    id("com.github.ben-manes.versions")

    //needed to add classpath to script
    id("com.jfrog.bintray") apply false
    id("com.jfrog.artifactory") apply false
}

allprojects {
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/kotlinx")
        mavenCentral()
    }
}

subprojects {
    tasks.withType<Test> {
        jvmArgs("-Xmx1g", "-XX:+UseParallelGC")
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
//        explicitApiWarning() //TODO change to strict before release
            targets.all {
                when (this) {
                    is KotlinJsTarget -> {
                        useCommonJs()
                        //configure running tests for JS
                        nodejs {
                            testTask {
                                useMocha {
                                    timeout = "600s"
                                }
                            }
                        }
                        browser {
                            testTask {
                                useKarma {
                                    useConfigDirectory(rootDir.resolve("js").resolve("karma.config.d"))
                                    useChromeHeadless()
                                }
                            }
                        }
                    }
                    is KotlinJvmTarget -> {
                        compilations.all {
                            kotlinOptions { jvmTarget = "1.6" }
                        }
                    }
                }
            }

            sourceSets.all {
                languageSettings.apply {
                    progressiveMode = true
                    languageVersion = "1.4"
                    apiVersion = "1.4"

                    useExperimentalAnnotation("kotlin.RequiresOptIn")

                    if (name.contains("test", ignoreCase = true) || project.name == "rsocket-test") {
                        useExperimentalAnnotation("kotlin.time.ExperimentalTime")
                        useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
                        useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
                        useExperimentalAnnotation("kotlinx.coroutines.InternalCoroutinesApi")
                        useExperimentalAnnotation("kotlinx.coroutines.ObsoleteCoroutinesApi")
                        useExperimentalAnnotation("kotlinx.coroutines.FlowPreview")
                        useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
                        useExperimentalAnnotation("io.ktor.util.InternalAPI")
                    }
                }
            }

            if (project.name != "rsocket-test") {
                val commonTest by sourceSets.getting {
                    dependencies {
                        implementation(project(":rsocket-test"))
                    }
                }
            }

            //fix atomicfu for examples and playground
            if ("examples" in project.path || project.name == "playground") {
                val commonMain by sourceSets.getting {
                    dependencies {
                        implementation("org.jetbrains.kotlinx:atomicfu:$kotlinxAtomicfuVersion")
                    }
                }
            }
        }
    }
}


//publication
subprojects {

    val versionSuffix: String? by project
    if (versionSuffix != null) {
        project.version = project.version.toString() + versionSuffix
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.all {
                mavenPublication {
                    pom {
                        name.set(project.name)
                        description.set(project.description)
                        url.set("http://rsocket.io")

                        licenses {
                            license {
                                name.set("The Apache Software License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("whyoleg")
                                name.set("Oleg Yukhnevich")
                                email.set("whyoleg@gmail.com")
                            }
                            developer {
                                id.set("OlegDokuka")
                                name.set("Oleh Dokuka")
                                email.set("oleh.dokuka@icloud.com")
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/rsocket/rsocket-kotlin.git")
                            developerConnection.set("scm:git:https://github.com/rsocket/rsocket-kotlin.git")
                            url.set("https://github.com/rsocket/rsocket-kotlin")
                        }
                    }
                }
            }
        }
    }
}

val bintrayUser: String? by project
val bintrayKey: String? by project
if (bintrayUser != null && bintrayKey != null) {

    //configure artifactory
    subprojects {
        plugins.withId("com.jfrog.artifactory") {
            configure<ArtifactoryPluginConvention> {
                setContextUrl("https://oss.jfrog.org")

                publish(delegateClosureOf<PublisherConfig> {
                    repository(delegateClosureOf<DoubleDelegateWrapper> {
                        setProperty("repoKey", "oss-snapshot-local")
                        setProperty("username", bintrayUser)
                        setProperty("password", bintrayKey)
                        setProperty("maven", true)
                    })
                    defaults(delegateClosureOf<groovy.lang.GroovyObject> {
                        invokeMethod("publications", arrayOf("kotlinMultiplatform", "metadata", "jvm", "js"))
                    })
                })

                val buildNumber: String? by project

                if (buildNumber != null) {
                    clientConfig.info.buildNumber = buildNumber
                }
            }
        }
    }

    //configure bintray / maven central
    val sonatypeUsername: String? by project
    val sonatypePassword: String? by project
    if (sonatypeUsername != null && sonatypePassword != null) {
        subprojects {
            plugins.withId("com.jfrog.bintray") {
                extensions.configure<BintrayExtension> {
                    user = bintrayUser
                    key = bintrayKey
                    setPublications("kotlinMultiplatform", "metadata", "jvm", "js")

                    publish = true
                    override = true
                    pkg.apply {

                        repo = "RSocket"
                        name = "rsocket-kotlin"

                        setLicenses("Apache-2.0")

                        issueTrackerUrl = "https://github.com/rsocket/rsocket-kotlin/issues"
                        websiteUrl = "https://github.com/rsocket/rsocket-kotlin"
                        vcsUrl = "https://github.com/rsocket/rsocket-kotlin.git"

                        githubRepo = "rsocket/rsocket-kotlin"
                        githubReleaseNotesFile = "README.md"

                        version.apply {
                            name = project.version.toString()
                            vcsTag = project.version.toString()
                            released = java.util.Date().toString()

                            gpg.sign = true

                            mavenCentralSync.apply {
                                user = sonatypeUsername
                                password = sonatypePassword
                            }
                        }
                    }
                }

                //workaround for https://github.com/bintray/gradle-bintray-plugin/issues/229
                tasks.withType<BintrayUploadTask> {
                    doFirst {
                        val publishing: PublishingExtension by extensions
                        publishing.publications
                            .filterIsInstance<MavenPublication>()
                            .forEach { publication ->
                                val moduleFile = buildDir.resolve("publications/${publication.name}/module.json")
                                if (moduleFile.exists()) {
                                    publication.artifact(object : FileBasedMavenArtifact(moduleFile) {
                                        override fun getDefaultExtension() = "module"
                                    })
                                }

                            }
                    }
                }
            }
        }
    }
}
