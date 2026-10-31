plugins {
    id("dev.kikugie.loom-back-compat")
    id("me.modmuss50.mod-publish-plugin")
}

val minecraftVersion = stonecutter.current.version
val modId = property("mod.id") as String
version = "${property("mod.version")}+$minecraftVersion-fabric"
group = property("mod.group") as String
base.archivesName = modId

repositories {
    exclusiveContent {
        forRepositories(maven("https://raw.githubusercontent.com/Rasa-Novum/Rosetta_Library/390046da82505320eec2dbc7b05e1d4747d92fa7/"))
        filter {
            includeModule("com.rasanovum.rosetta", "rosetta-1.21.1-fabric")
        }
    }
    maven("https://repo.sleeping.town/")
    maven("https://raw.githubusercontent.com/Rasa-Novum/Rosetta_Library/maven/")
    maven("https://maven.ladysnake.org/releases")
    maven("https://maven.theillusivec4.top/")
    maven("https://maven.syrupstudios.net/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("net.syrupstudios:syrup_library:${property("deps.syrup_library")}+$minecraftVersion-fabric")
    compileOnly("de.marhali:json5-java:3.0.0")
    implementation("com.electronwill.night-config:core:3.8.3")
    implementation("com.electronwill.night-config:toml:3.8.3")
    include("com.electronwill.night-config:core:3.8.3")
    include("com.electronwill.night-config:toml:3.8.3")

    modCompileOnly("dev.emi:trinkets:${property("deps.trinkets")}")
    val rosetta = "com.rasanovum.rosetta:rosetta-1.21.1-fabric:${property("deps.rosetta")}"
    modImplementation(rosetta)
    include(rosetta)
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.configureEach {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("CREDITS")) { into("META-INF") }
}

tasks.processResources {
    val values = mapOf(
        "version" to project.version,
        "modId" to modId,
        "modName" to project.property("mod.name"),
        "modDescription" to project.property("mod.description"),
        "license" to project.property("mod.license"),
        "mc" to minecraftVersion,
        "fl" to project.property("deps.fabric_loader"),
        "fapi" to project.property("deps.fabric_api"),
        "rosetta" to project.property("deps.rosetta"),
        "syrupLibrary" to project.property("deps.syrup_library"),
        "authors" to (project.property("mod.authors") as String).split(", ").joinToString("\",\n\t\t\""),
        "contributors" to (project.property("mod.contributors") as String).split(", ").joinToString("\",\n\t\t\""),
        "homepage" to project.property("mod.homepage"),
        "issues" to project.property("mod.issues"),
        "sources" to project.property("mod.sources"),
    )
    inputs.properties(values)
    exclude("META-INF/neoforge.mods.toml")
    filesMatching("fabric.mod.json") { expand(values) }
}

val modrinthToken = providers.gradleProperty("publish.modrinth_token")
    .orElse(providers.gradleProperty("MODRINTH_TOKEN"))
    .orElse(providers.environmentVariable("MODRINTH_TOKEN"))
val curseForgeToken = providers.gradleProperty("publish.curseforge_token")
    .orElse(providers.gradleProperty("CURSEFORGE_TOKEN"))
    .orElse(providers.environmentVariable("CURSEFORGE_TOKEN"))
val modrinthId = property("publish.modrinth").toString().trim()
val curseForgeId = property("publish.curseforge").toString().trim()
val publishDryRun = providers.gradleProperty("publish.dry_run").map {
    when (it) {
        "true" -> true
        "false" -> false
        else -> throw GradleException("publish.dry_run must be true or false")
    }
}.orElse(!modrinthToken.isPresent || !curseForgeToken.isPresent || modrinthId.isBlank() || curseForgeId.isBlank())
val uploadVersion = "${property("mod.version")}+$minecraftVersion-fabric"
val compatibleVersions = stonecutter.properties.rawOrNull("mod.mc_releases")
    ?.asList().orEmpty().map { it.toString() }

if (!publishDryRun.get()) {
    if (modrinthId.isBlank() || curseForgeId.isBlank()) {
        throw GradleException("Set publish.modrinth and publish.curseforge in stonecutter.properties.yaml before live publishing")
    }
    if (!modrinthToken.isPresent || !curseForgeToken.isPresent) {
        throw GradleException("Live publishing needs MODRINTH_TOKEN and CURSEFORGE_TOKEN (or Gradle publish.*_token properties)")
    }
}

publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }
    dryRun.set(publishDryRun)
    version = uploadVersion
    displayName = "${property("mod.name")} ${property("mod.version")} - Fabric $minecraftVersion"
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText
    type = when (property("publish.release_type").toString().lowercase()) {
        "stable" -> STABLE
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> throw GradleException("publish.release_type must be stable, beta, or alpha")
    }
    modLoaders.add("fabric")

    modrinth {
        projectId = modrinthId.ifBlank { "00000000" }
        accessToken = modrinthToken
        compatibleVersions.forEach { minecraftVersions.add(it) }
        environment = CLIENT_AND_SERVER
        requires("syrup-library")
        requires("fabric-api")
    }
    curseforge {
        projectId = curseForgeId.ifBlank { "0" }
        accessToken = curseForgeToken
        compatibleVersions.forEach { minecraftVersions.add(it) }
        client = true
        server = true
        requires("syrup-library")
        requires("fabric-api")
    }
}
