plugins {
    java
}

allprojects {
    group = "com.manhhavu"
    version = "0.1.0-SNAPSHOT"
}

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
}
