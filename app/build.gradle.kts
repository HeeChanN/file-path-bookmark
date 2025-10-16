plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("one.microstream:microstream-storage-embedded:08.01.02-MS-GA")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Swing UI
    implementation("com.formdev:flatlaf:3.6.2")              // Java 8+ 지원
    implementation("com.formdev:flatlaf-extras:3.6.2")
    implementation("com.miglayout:miglayout-swing:11.4.2")
}

tasks.test {
    useJUnitPlatform()
}
