plugins {
    `java-library`
    `maven-publish`
    id("io.quarkus") version "3.12.0.CR1"
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "org.gamboni"
version = "0.0.3-SNAPSHOT"

dependencies {
    val lombok = "org.projectlombok:lombok:1.18.30"
    compileOnly(lombok)
    annotationProcessor(lombok)

    implementation(libs.guava)

    api(project(":tech-history"))
    implementation("io.quarkus:quarkus-hibernate-orm:3.5.0")

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
        create<MavenPublication>("tech-persistence") {
            from(components["java"])
        }
    }
}