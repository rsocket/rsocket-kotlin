import groovy.util.*
import org.gradle.api.publish.maven.internal.artifact.*
import org.jetbrains.kotlin.konan.target.*
import org.jfrog.gradle.plugin.artifactory.dsl.*

//TODO cleanup publication

plugins {
    signing
    `maven-publish`
    com.jfrog.artifactory
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

    kotlin {
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

    tasks.withType<PublishToMavenRepository> {
        dependsOn(tasks.withType<Sign>())
    }

    tasks.matching { it.name == "generatePomFileForKotlinMultiplatformPublication" }.configureEach {
        dependsOn(tasks["generatePomFileForJvmPublication"])
    }
}

val bintrayUser: String? by project
val bintrayKey: String? by project
if (bintrayUser != null && bintrayKey != null) {
    //configure artifactory
    artifactory {
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

//configure maven central
val sonatypeUsername: String? by project
val sonatypePassword: String? by project
if (sonatypeUsername != null && sonatypePassword != null) {
    afterEvaluate {
        publishing {
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

                when (val type = it.name) {
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
        signing {
            //requiring signature if there is a publish task that is not to MavenLocal
            isRequired = gradle.taskGraph.allTasks.any {
                it.name.toLowerCase().contains("publish") && !it.name.contains("MavenLocal")
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
                        val moduleFile = buildDir.resolve("publications/${publication.name}/module.json")
                        if (moduleFile.exists()) {
                            publication.artifact(object : FileBasedMavenArtifact(moduleFile) {
                                override fun getDefaultExtension() = "module"
                            })
                        }
                    }
            }
            afterEvaluate {
                sign(*publishing.publications.toTypedArray())
            }
        }
    }
}
