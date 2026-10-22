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

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")

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
