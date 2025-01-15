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
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.joml", "joml", "1.10.8")
    implementation("com.graphhopper:jsprit-core:1.8")
    implementation("com.graphhopper:jsprit-analysis:1.8")
}

tasks.test {
    useJUnitPlatform()
}