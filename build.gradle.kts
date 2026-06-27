val ktor_version: String = "3.0.0"
val kotlin_version: String = "2.0.0"

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    // Plugin-ul de Ktor 3.0.0 este corect
    id("io.ktor.plugin") version "3.0.0"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server 3.0.0 Core & Netty
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")

    // Serializare JSON pentru Ktor 3
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")

    // CORS pentru Ktor 3 (REPARĂ EROAREA 403)
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")

    // Serializare Kotlinx nativă
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Baza de date (Exposed)
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("io.ktor:ktor-client-core:3.0.0")
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    implementation("io.ktor:ktor-server-auth:3.0.0")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.0")
    implementation("com.auth0:java-jwt:4.4.0")

    implementation("org.mindrot:jbcrypt:0.4")



    // TESTE - În Ktor 3, test-host este varianta preferată în loc de server-tests în anumite configuri
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}