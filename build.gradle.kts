plugins {
    id("java")
    id("com.gradleup.shadow") version "9.5.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("com.github.retrooper:packetevents-spigot:2.14.0") {
        exclude(group = "net.kyori", module = "adventure-api")
        exclude(group = "net.kyori", module = "adventure-key")
        exclude(group = "org.jetbrains", module = "annotations")
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.jar {
    enabled = false
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.github.retrooper.packetevents", "me.Thomas.HCF.Library.PacketEvents.Api")
    relocate("io.github.retrooper.packetevents", "me.Thomas.HCF.Library.PacketEvents.Impl")
    relocate("net.kyori.adventure.nbt", "me.Thomas.HCF.Library.AdventureNbt") {
        exclude("net.kyori.adventure.nbt.api.**")
    }
    relocate("net.kyori.examination", "me.Thomas.HCF.Library.Examination")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
