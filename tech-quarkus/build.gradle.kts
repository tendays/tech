plugins {
    `java-library`
    `maven-publish`
    // redundant? id("io.quarkus") version "3.15.1"
    id("io.quarkus.extension") version "3.15.1"
}

group = "org.gamboni"
version = "0.0.3-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

quarkusExtension {
    deploymentModule = "tech-quarkus-deployment"
}

dependencies {
    val lombok = "org.projectlombok:lombok:1.18.30"

    implementation(platform("io.quarkus:quarkus-bom:3.15.1"))

    api(project(":tech-history"))
    api(project(":tech-web"))
    annotationProcessor(project(":tech-web"))


    implementation("io.quarkus:quarkus-websockets:3.5.0")
    implementation("io.quarkus:quarkus-resteasy-reactive:3.5.0")

    compileOnly(lombok)
    annotationProcessor(lombok)

    implementation(libs.guava)
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("tech-quarkus") {
            from(components["java"])
        }
    }
}