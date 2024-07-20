plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "org.gamboni"
version = "0.0.2-SNAPSHOT"

dependencies {
    val lombok = "org.projectlombok:lombok:1.18.30"

    api(project(":tech-web"))
    annotationProcessor(project(":tech-web"))

    compileOnly(lombok)
    annotationProcessor(lombok)
    
    implementation(libs.guava)
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

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
        create<MavenPublication>("tech-history") {
            from(components["java"])
        }
    }
}