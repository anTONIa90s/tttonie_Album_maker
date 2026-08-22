plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.2")

    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("tiptoieditor.Launcher")
}

tasks.named<Sync>("installDist") {
    from("tools") {
        into("lib/tools")
        include("*.exe", "*.dll")
    }
}

tasks.test {
    useJUnitPlatform()
}
