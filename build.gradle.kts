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

import groovy.util.*
import org.gradle.api.publish.maven.internal.artifact.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.konan.target.*
import org.jfrog.gradle.plugin.artifactory.dsl.*

buildscript {
    repositories {
        mavenCentral()
    }
    val kotlinVersion: String by rootProject
    val kotlinxAtomicfuVersion: String by rootProject

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$kotlinxAtomicfuVersion")
    }
}

val kotlinxAtomicfuVersion: String by rootProject

plugins {
    id("com.github.ben-manes.versions")

    //needed to add classpath to script
    id("com.jfrog.artifactory") apply false
}

val Project.publicationNames: Array<String>
    get() {
        val publishing: PublishingExtension by extensions
        val all = publishing.publications.names
        //publish js, jvm, metadata, linuxX64 and kotlinMultiplatform only once
        return when {
            HostManager.hostIsLinux -> all
            else                    -> all - "js" - "jvm" - "metadata" - "kotlinMultiplatform" - "linuxX64"
        }.toTypedArray()
    }

subprojects {
    tasks.whenTaskAdded {
        if (name.endsWith("test", ignoreCase = true)) onlyIf { !rootProject.hasProperty("skipTests") }
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        //targets configuration
        extensions.configure<KotlinMultiplatformExtension> {
            val isAutoConfigurable = project.name.startsWith("rsocket") //manual config of others
            val jvmOnly =
                project.name == "rsocket-transport-ktor-server" || //server is jvm only
                        project.name == "rsocket-test-server"
            //windows target isn't supported by ktor-network
            val supportMingw = project.name != "rsocket-transport-ktor" && project.name != "rsocket-transport-ktor-client"


            if (!isAutoConfigurable) return@configure

            jvm {
                testRuns.all {
                    executionTask.configure {
                        // ActiveProcessorCount is used here, to make sure local setup is similar as on CI
                        // Github Actions linux runners have 2 cores
                        jvmArgs("-Xmx4g", "-XX:+UseParallelGC", "-XX:ActiveProcessorCount=2")
                    }
                }
            }

            if (jvmOnly) return@configure

            js {
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

            //native targets configuration
            val hostTargets = listOfNotNull(linuxX64(), macosX64(), if (supportMingw) mingwX64() else null)

            val iosTargets = listOf(iosArm32(), iosArm64(), iosX64())
            val tvosTargets = listOf(tvosArm64(), tvosX64())
            val watchosTargets = listOf(watchosArm32(), watchosArm64(), watchosX86())
            val nativeTargets = hostTargets + iosTargets + tvosTargets + watchosTargets

            val nativeMain by sourceSets.creating {
                dependsOn(sourceSets["commonMain"])
            }
            val nativeTest by sourceSets.creating {
                dependsOn(sourceSets["commonTest"])
            }

            nativeTargets.forEach {
                sourceSets["${it.name}Main"].dependsOn(nativeMain)
                sourceSets["${it.name}Test"].dependsOn(nativeTest)
            }

            //run tests on release + mimalloc to reduce tests execution time
            //compilation is slower in that mode, but work with buffers is much faster
            if (System.getenv("CI") == "true") {
                targets.all {
                    if (this is KotlinNativeTargetWithTests<*>) {
                        binaries.test(listOf(RELEASE))
                        testRuns.all { setExecutionSourceFrom(binaries.getTest(RELEASE)) }
                        compilations.all {
                            kotlinOptions.freeCompilerArgs += "-Xallocator=mimalloc"
                        }
                    }
                }
            }
        }

        //common configuration
        extensions.configure<KotlinMultiplatformExtension> {
            val isTestProject = project.name == "rsocket-test" || project.name == "rsocket-test-server"
            val isLibProject = project.name.startsWith("rsocket")
            val isPlaygroundProject = project.name == "playground"
            val isExampleProject = "examples" in project.path

            sourceSets.all {
                languageSettings.apply {
                    progressiveMode = true
                    languageVersion = "1.5"
                    apiVersion = "1.5"

                    optIn("kotlin.RequiresOptIn")

                    //TODO: kludge, this is needed now, as ktor isn't fully supports kotlin 1.5.3x opt-in changes
                    // will be not needed in future with ktor 2.0.0
                    optIn("io.ktor.utils.io.core.ExperimentalIoApi")

                    if (name.contains("test", ignoreCase = true) || isTestProject || isPlaygroundProject) {
                        optIn("kotlin.time.ExperimentalTime")
                        optIn("kotlin.ExperimentalStdlibApi")

                        optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                        optIn("kotlinx.coroutines.InternalCoroutinesApi")
                        optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
                        optIn("kotlinx.coroutines.FlowPreview")
                        optIn("kotlinx.coroutines.DelicateCoroutinesApi")

                        optIn("io.ktor.util.InternalAPI")
                        optIn("io.ktor.utils.io.core.internal.DangerousInternalIoApi")

                        optIn("io.rsocket.kotlin.TransportApi")
                        optIn("io.rsocket.kotlin.ExperimentalMetadataApi")
                        optIn("io.rsocket.kotlin.ExperimentalStreamsApi")
                        optIn("io.rsocket.kotlin.RSocketLoggingApi")
                    }
                }
            }

            if (isLibProject && !isTestProject) {
                explicitApi()
                sourceSets["commonTest"].dependencies {
                    implementation(project(":rsocket-test"))
                }
            }

            //fix atomicfu for examples and playground
            if (isExampleProject || isPlaygroundProject) {
                sourceSets["commonMain"].dependencies {
                    implementation("org.jetbrains.kotlinx:atomicfu:$kotlinxAtomicfuVersion")
                }
            }
        }
    }
}

fun publishPlatformArtifactsInRootModule(
    platformPublication: MavenPublication,
    kotlinMultiplatformPublication: MavenPublication
) {
    lateinit var platformXml: XmlProvider

    platformPublication.pom.withXml { platformXml = this }

    kotlinMultiplatformPublication.apply {
        pom.withXml {
            val root = asNode()
            // Remove the original content and add the content from the platform POM:
            root.children().toList().forEach { root.remove(it as Node) }
            platformXml.asNode().children()
                .forEach { root.append(it as Node) }

            // Adjust the self artifact ID, as it should match the root module's coordinates:
            ((root.get("artifactId") as NodeList)[0] as Node).setValue(artifactId)

            // Set packaging to POM to indicate that there's no artifact:
            root.appendNode("packaging", "pom")

            // Remove the original platform dependencies and add a single dependency on the platform module:
            val dependencies = (root.get("dependencies") as NodeList)[0] as Node
            dependencies.children().toList()
                .forEach { dependencies.remove(it as Node) }
            val singleDependency = dependencies.appendNode("dependency")
            singleDependency.appendNode(
                "groupId",
                platformPublication.groupId
            )
            singleDependency.appendNode(
                "artifactId",
                platformPublication.artifactId
            )
            singleDependency.appendNode(
                "version",
                platformPublication.version
            )
            singleDependency.appendNode("scope", "compile")
        }
    }

    tasks.matching { it.name == "generatePomFileForKotlinMultiplatformPublication" }
        .configureEach {
            dependsOn(tasks["generatePomFileFor${platformPublication.name.capitalize()}Publication"])
        }

}

//publication
subprojects {
    afterEvaluate {

        val versionSuffix: String? by project
        if (versionSuffix != null) {
            project.version = project.version.toString() + versionSuffix
        }

        task<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
        }

        tasks.withType<Sign> {
            dependsOn("javadocJar")
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
                                connection.set("https://github.com/rsocket/rsocket-kotlin.git")
                                developerConnection.set("https://github.com/rsocket/rsocket-kotlin.git")
                                url.set("https://github.com/rsocket/rsocket-kotlin")
                            }
                        }
                    }
                }
            }
        }

        tasks.withType<PublishToMavenRepository> {
            dependsOn(tasks.withType<Sign>())
        }

        tasks.matching { it.name == "generatePomFileForKotlinMultiplatformPublication" }.configureEach {
            dependsOn(tasks["generatePomFileForJvmPublication"])
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
                    println("Artifactory: ${publicationNames.contentToString()}")
                    defaults(delegateClosureOf<groovy.lang.GroovyObject> {
                        invokeMethod("publications", publicationNames)
                    })
                })

                val buildNumber: String? by project

                if (buildNumber != null) {
                    clientConfig.info.buildNumber = buildNumber
                }
            }
        }
    }
}

//configure bintray / maven central
val sonatypeUsername: String? by project
val sonatypePassword: String? by project
if (sonatypeUsername != null && sonatypePassword != null) {
    subprojects {
        afterEvaluate {
            plugins.withId("maven-publish") {
                plugins.withId("signing") {
                    extensions.configure<SigningExtension> {
                        //requiring signature if there is a publish task that is not to MavenLocal
                        isRequired = gradle.taskGraph.allTasks.any {
                            it.name.toLowerCase()
                                .contains("publish") && !it.name.contains("MavenLocal")
                        }

                        val signingKey: String? by project
                        val signingPassword: String? by project

                        useInMemoryPgpKeys(signingKey, signingPassword)
                        val names = publicationNames
                        val publishing: PublishingExtension by project.extensions
                        beforeEvaluate {
                            publishing.publications
                                .filterIsInstance<MavenPublication>()
                                .filter { it.name in names }
                                .forEach { publication ->
                                    val moduleFile =
                                        buildDir.resolve("publications/${publication.name}/module.json")
                                    if (moduleFile.exists()) {
                                        publication.artifact(object :
                                            FileBasedMavenArtifact(moduleFile) {
                                            override fun getDefaultExtension() = "module"
                                        })
                                    }
                                }
                        }
                        afterEvaluate {
                            sign(*publishing.publications.toTypedArray())
                        }
                    }

                    extensions.configure<PublishingExtension> {
                        repositories {
                            maven {
                                name = "sonatype"
                                url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                                credentials {
                                    username = sonatypeUsername
                                    password = sonatypePassword
                                }
                            }
                        }

                        publications.filterIsInstance<MavenPublication>().forEach {
                            // add empty javadocs
                            if (name != "kotlinMultiplatform") {
                                it.artifact(tasks["javadocJar"])
                            }

                            val type = it.name
                            when (type) {
                                "kotlinMultiplatform" -> {
                                    // With Kotlin 1.4 & HMPP, the root module should have no suffix in the ID, but for compatibility with
                                    // the consumers who can't read Gradle module metadata, we publish the JVM artifacts in it, too
                                    it.artifactId = project.name
                                    publishPlatformArtifactsInRootModule(
                                        publications["jvm"] as MavenPublication,
                                        it
                                    )
                                }
                                else                  -> it.artifactId = "${project.name}-$type"
                            }
                        }
                    }
                }
            }
        }
    }
}
