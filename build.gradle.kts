plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "dev.roam"
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.helpch.at/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.william278.net/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Optional plugins, all of them are only used when installed.
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.9") { isTransitive = false }
    compileOnly("com.github.TechFortress:GriefPrevention:16.18.4") { isTransitive = false }
    compileOnly("net.william278.huskclaims:huskclaims-bukkit:1.5.10") { isTransitive = false }
    compileOnly("net.william278.husktowns:husktowns-bukkit:3.0") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        archiveFileName = "Roam-${project.version}.jar"
    }

    runServer {
        minecraftVersion("1.21.11")
    }
}
