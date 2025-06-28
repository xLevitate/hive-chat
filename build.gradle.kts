plugins {
    id("java")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.levitate"
version = "1.4.3"

repositories {
    mavenCentral()
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier.set("")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("hive-chat")
                description.set("A library for handling Minecraft messages in a more powerful and intuitive manner.")
            }
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}