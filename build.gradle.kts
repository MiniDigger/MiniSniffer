plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.7.0"
    application
}

group = "dev.benndorf.minisniffer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    target.compilations.all {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    val doodleVersion = "0.10.1"
    val ktorVersion = "2.3.11"

    dependencies {
        val osName = System.getProperty("os.name")
        val targetOs = when {
            osName == "Mac OS X" -> "macos"
            osName.startsWith("Win") -> "windows"
            osName.startsWith("Linux") -> "linux"
            else -> error("Unsupported OS: $osName")
        }

        val osArch = System.getProperty("os.arch")
        val targetArch = when (osArch) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported arch: $osArch")
        }

        val target = "$targetOs-$targetArch"

        implementation("io.nacular.doodle:core:$doodleVersion")
        implementation("io.nacular.doodle:desktop-jvm-$target:$doodleVersion")
        implementation("io.nacular.doodle:controls:$doodleVersion")
        implementation("io.nacular.doodle:animation:$doodleVersion")
        implementation("io.nacular.doodle:themes:$doodleVersion")

        implementation("io.ktor:ktor-network:$ktorVersion")
        implementation("com.benasher44:uuid:0.3.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    }
}

application {
    mainClass.set("dev.benndorf.minisniffer.MiniSnifferApplication")
}
