/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java library project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.7/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    `maven-publish`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    mavenLocal()
}

group = "org.gamboni"
version = "0.0.3-SNAPSHOT"

dependencies {
    val lombok = "org.projectlombok:lombok:1.18.30"
    compileOnly(lombok)
    annotationProcessor(lombok)

    val autoService = "com.google.auto.service:auto-service:1.1.1"
    compileOnly(autoService)
    annotationProcessor(autoService)
    implementation(libs.guava)
    implementation("org.slf4j:slf4j-api:2.0.13")
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}


publishing {
    publications {
        create<MavenPublication>("tech-web") {
            from(components["java"])
        }
    }
}