pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.7.11"
}

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) = loaders
            .forEach { loader ->
                val buildscript = if (loader == "fabric" && stonecutter.eval(version, "<26.1")) {
                    "build.fabric_remap.gradle.kts"
                } else {
                    "build.$loader.gradle.kts"
                }
                version("$version-$loader", version).buildscript = buildscript
            }

        match("1.21.11", "fabric", "neoforge")
        match("26.1", "fabric", "neoforge")

        vcsVersion = "26.1-fabric"
    }
}
