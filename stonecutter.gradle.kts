plugins {
    id("dev.kikugie.stonecutter")
    id("org.sonarqube") version "7.5.0.8588"
}

sonar {
    properties {
        property("sonar.projectKey", "Syrup-Studios_atlasbound")
        property("sonar.organization", "syrup-studios")
    }
}

stonecutter active "1.21.1-fabric" /* [SC] DO NOT EDIT */

stonecutter {
    parameters {
        val (version, loader) = current.project.split('-', limit = 2)
        properties { tags(version, loader) }
        constants.match(loader, "fabric", "neoforge")
    }
}
