plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/repositories/sumo-releases/")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.eclipse.sumo:libtraci:1.21.0")
    implementation("org.joml", "joml", "1.10.8")
}

tasks.test {
    useJUnitPlatform()
}