plugins {
    java
    `maven-publish`
    id("org.jreleaser") version "1.15.0" apply false
}

allprojects {
    group = "com.manhhavu"
    version = rootProject.findProperty("version") as String? ?: "0.1.0-SNAPSHOT"
}

val publishableModules = setOf("webrtc-audio-processing", "webrtc-audio-processing-natives")

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    if (name in publishableModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "org.jreleaser")

        java {
            withSourcesJar()
            withJavadocJar()
        }

        publishing {
            publications {
                create<MavenPublication>("maven") {
                    groupId = "com.manhhavu"
                    artifactId = project.name
                    from(components["java"])

                    pom {
                        name.set(project.name)
                        description.set("Java wrapper for WebRTC audio processing (echo cancellation, noise suppression, gain control)")
                        url.set("https://github.com/manhhavu/webrtc-audio-processing-java")
                        inceptionYear.set("2026")

                        licenses {
                            license {
                                name.set("BSD-3-Clause")
                                url.set("https://opensource.org/licenses/BSD-3-Clause")
                            }
                        }

                        developers {
                            developer {
                                id.set("manhhavu")
                                name.set("Manh Ha VU")
                                url.set("https://github.com/manhhavu")
                            }
                        }

                        scm {
                            connection.set("scm:git:https://github.com/manhhavu/webrtc-audio-processing-java.git")
                            developerConnection.set("scm:git:ssh://github.com/manhhavu/webrtc-audio-processing-java.git")
                            url.set("https://github.com/manhhavu/webrtc-audio-processing-java")
                        }
                    }
                }
            }

            repositories {
                maven {
                    name = "staging"
                    url = uri(layout.buildDirectory.dir("staging-deploy"))
                }
            }
        }

        configure<org.jreleaser.gradle.plugin.JReleaserExtension> {
            project {
                copyright.set("Google Inc.")
            }

            signing {
                active.set(org.jreleaser.model.Active.ALWAYS)
                armored.set(true)
            }

            deploy {
                maven {
                    mavenCentral {
                        create("sonatype") {
                            active.set(org.jreleaser.model.Active.ALWAYS)
                            url.set("https://central.sonatype.com/api/v1/publisher")
                            stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
                        }
                    }
                }
            }
        }
    }
}
