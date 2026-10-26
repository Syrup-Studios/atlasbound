pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

rootProject.name = "atlasbound"

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) {
                version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
            }
        }

        match("1.21.1", "fabric", "neoforge")
        vcsVersion = "1.21.1-fabric"
    }
}
