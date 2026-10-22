plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.sergicraftero"
version = "2.0.0"
description = "Servidor de escape rooms: lobby, progresión, tienda y salas temáticas para Paper"

val paperVersion = "1.21.4-R0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// -PpaperApiJar=/path/paper-api.jar builds against a locally compiled API (when repo.papermc.io is unreachable).
val localPaperApi: String? = providers.gradleProperty("paperApiJar").orNull

dependencies {
    if (localPaperApi != null) {
        compileOnly(files(localPaperApi))
        compileOnly(platform("net.kyori:adventure-bom:4.20.0"))
        compileOnly("net.kyori:adventure-api")
        compileOnly("net.kyori:adventure-text-minimessage")
        compileOnly("net.kyori:adventure-text-serializer-plain")
        compileOnly("com.google.code.gson:gson:2.11.0")
        compileOnly("com.google.guava:guava:33.3.1-jre")
        compileOnly("org.joml:joml:1.10.8")
        compileOnly("org.jetbrains:annotations:26.0.1")
        compileOnly("org.jspecify:jspecify:1.0.0")
        compileOnly("net.md-5:bungeecord-chat:1.20-R0.2")
    } else {
        compileOnly("io.papermc.paper:paper-api:$paperVersion")
    }

    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// The resource pack lives in /resourcepack as plain files and is zipped into the plugin jar.
val zipResourcePack by tasks.registering(Zip::class) {
    from("resourcepack")
    archiveFileName.set("escaperooms-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
    processResources {
        filteringCharset = "UTF-8"
        from(zipResourcePack)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("failed")
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    runServer {
        minecraftVersion("1.21.4")
    }
}
