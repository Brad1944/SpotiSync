import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems.jar

plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "top.apip.spotisync"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

tasks.shadowJar {
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("se.michaelthelin.spotify:spotify-web-api-java:8.4.0")
    implementation("com.github.sealedtx:java-youtube-downloader:3.2.3")
    // Commons lang
    implementation("org.apache.commons:commons-lang3:+")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "top.apip.spotisync.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}