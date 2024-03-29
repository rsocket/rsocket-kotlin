/*
 * Copyright 2015-2023 the original author or authors.
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

plugins {
    `maven-publish`
    signing
    id("build-parameters")
}

val buildParameters = the<buildparameters.BuildParametersExtension>()

val versionSuffix = buildParameters.versionSuffix.orNull
if (versionSuffix != null) {
    val versionString = project.version.toString()
    require(versionString.endsWith("-SNAPSHOT"))
    project.version = versionString.replace("-", "-${versionSuffix}-")
    println("Current version: ${project.version}")
}

//empty javadoc for maven central
val javadocJar by tasks.registering(Jar::class) { archiveClassifier.set("javadoc") }

// this is somewhat a hack because we have a single javadoc artifact which is used for all publications
val signTasks = tasks.withType<Sign>()
signTasks.configureEach { dependsOn(javadocJar) }
tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn(signTasks) }

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar.get())

        afterEvaluate {
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

    //TODO: move to build parameters
    val githubUsername: String? by project
    val githubPassword: String? by project

    val sonatypeUsername: String? by project
    val sonatypePassword: String? by project

    val signingKey: String? by project
    val signingPassword: String? by project

    repositories {
        //TODO: drop github and use sonatype (?)
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/rsocket/rsocket-kotlin")
            credentials {
                username = githubUsername
                password = githubPassword
            }
        }
        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }

    signing {
        isRequired = sonatypeUsername != null && sonatypePassword != null &&
                signingKey != null && signingPassword != null

        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publications)
    }
}
