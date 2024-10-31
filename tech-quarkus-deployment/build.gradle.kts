plugins {
    `java-library`
    `maven-publish`
}

group = "org.gamboni"
version = "0.0.3-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.quarkus:quarkus-bom:3.15.1"))

    implementation("io.quarkus:quarkus-core-deployment")
    implementation(project(":tech-quarkus"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("io.quarkus:quarkus-websockets-next-deployment")
    implementation("io.quarkus:quarkus-rest-deployment")
    implementation("io.quarkus:quarkus-vertx-http-deployment")
    implementation("io.quarkus:quarkus-rest-common-deployment")
    implementation("io.quarkus:quarkus-tls-registry-deployment")
    implementation("io.quarkus:quarkus-vertx-deployment")
    implementation("io.quarkus:quarkus-virtual-threads-deployment")
    implementation("io.quarkus:quarkus-netty-deployment")
    implementation("io.quarkus:quarkus-jsonp-deployment")
    implementation("io.quarkus:quarkus-mutiny-deployment")
    implementation("io.quarkus:quarkus-smallrye-context-propagation-deployment")
    implementation("io.quarkus:quarkus-arc-deployment")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("tech-quarkus-deployment") {
            from(components["java"])
        }
    }
}