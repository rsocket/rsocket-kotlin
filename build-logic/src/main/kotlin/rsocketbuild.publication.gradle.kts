/*
 * Copyright 2015-2024 the original author or authors.
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

import com.vanniktech.maven.publish.*

plugins {
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01)
    signAllPublications()

    pom {
        name.set(project.name)
        description.set(provider {
            checkNotNull(project.description) { "Project description isn't set for project: ${project.path}" }
        })
        url.set("https://rsocket.io")

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

// javadocJar setup
// we have a single javadoc artifact which is used for all publications,
// and so we need to manually create task dependencies to make Gradle happy
val javadocJar by tasks.registering(Jar::class) { archiveClassifier.set("javadoc") }
tasks.withType<Sign>().configureEach { dependsOn(javadocJar) }
tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn(tasks.withType<Sign>()) }
publishing.publications.withType<MavenPublication>().configureEach { artifact(javadocJar) }
