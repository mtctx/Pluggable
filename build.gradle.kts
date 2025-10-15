/*
 *     Pluggable: build.gradle.kts
 *     Copyright (C) 2025 mtctx
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("org.jetbrains.dokka") version "2.1.0-Beta"
    id("org.jetbrains.dokka-javadoc") version "2.1.0-Beta"
    signing
}

group = "dev.mtctx.library"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api("dev.mtctx.library:utilities:1.0.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.1.0-Beta")
}

mavenPublishing {
    coordinates(group.toString(), "plugabble", version.toString())

    pom {
        name.set("Pluggable")
        description.set("Add a Plugin system easily to your Kotlin project!")
        inceptionYear.set("2025")
        url.set("https://github.com/mtctx/Pluggable")

        licenses {
            license {
                name.set("GNU General Public License v3.0")
                url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                distribution.set("repo")
            }
        }

        scm {
            url.set("https://github.com/mtctx/Pluggable")
            connection.set("scm:git:git@github.com:mtctx/Pluggable.git")
            developerConnection.set("scm:git:ssh://git@github.com:mtctx/Pluggable.git")
        }

        developers {
            developer {
                id.set("mtctx")
                name.set("mtctx")
                email.set("me@mtctx.dev")
            }
        }
    }

    configure(KotlinJvm(JavadocJar.Dokka("dokkaGenerateJavadoc"), sourcesJar = true))

    signAllPublications()
    publishToMavenCentral(automaticRelease = true)
}

signing {
    useGpgCmd()
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html").get().asFile)
    }
    dokkaPublications.javadoc {
        outputDirectory.set(layout.buildDirectory.dir("dokka/javadoc").get().asFile)
    }

    dokkaSourceSets.configureEach {
        jdkVersion.set(21)
        sourceLink {
            localDirectory.set(file("${project.name}/src/main/kotlin"))
            remoteUrl.set(uri("https://github.com/mtctx/Squishy/tree/main/${project.name}/src/main/kotlin/"))
            remoteLineSuffix.set("#L")
        }
    }
}

val javaTarget = 21
kotlin {
    jvmToolchain(javaTarget)
}