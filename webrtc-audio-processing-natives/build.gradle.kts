plugins {
    `java-library`
}

// Native libs are placed here by the cargo build task or CI:
//   build/natives/macos-aarch64/libapm.dylib
//   build/natives/linux-x86_64/libapm.so

val nativesDir = layout.buildDirectory.dir("natives")

tasks.register("cargoReleaseBuild") {
    description = "Build the Rust native library for the current platform"
    doLast {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch")
        val platform = when {
            "mac" in os && arch == "aarch64" -> "macos-aarch64"
            "mac" in os -> "macos-x86_64"
            "linux" in os && (arch == "amd64" || arch == "x86_64") -> "linux-x86_64"
            "linux" in os && arch == "aarch64" -> "linux-aarch64"
            else -> error("Unsupported platform: $os $arch")
        }
        val libName = if ("mac" in os) "libapm.dylib" else "libapm.so"

        exec {
            workingDir = rootProject.file("native")
            commandLine("cargo", "build", "--release")
        }

        val src = rootProject.file("native/target/release/$libName")
        val dest = nativesDir.get().dir(platform).file(libName).asFile
        dest.parentFile.mkdirs()
        src.copyTo(dest, overwrite = true)
    }
}

// For each platform, create a JAR with classifier
val platforms = listOf("macos-aarch64", "linux-x86_64")
platforms.forEach { platform ->
    tasks.register<Jar>("${platform}Jar") {
        archiveClassifier.set(platform)
        from(nativesDir.map { it.dir(platform) }) {
            into("native/$platform")
        }
    }
}

tasks.named("assemble") {
    dependsOn(platforms.map { "${it}Jar" })
}
