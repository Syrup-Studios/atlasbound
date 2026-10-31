plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("me.modmuss50.mod-publish-plugin")
}

val minecraftVersion = stonecutter.current.version
val modId = property("mod.id") as String
val syrupLibraryVersion = "${property("deps.syrup_library")}+${minecraftVersion}-neoforge"
version = "${property("mod.version")}+$minecraftVersion-neoforge"
group = property("mod.group") as String
base.archivesName = modId

repositories {
    maven("https://repo.sleeping.town/")
    maven("https://raw.githubusercontent.com/Rasa-Novum/Rosetta_Library/maven/")
    maven("https://maven.ladysnake.org/releases")
    maven("https://maven.theillusivec4.top/")
    maven("https://maven.syrupstudios.net/releases/")
}

dependencies {
    implementation("net.syrupstudios:syrup_library:$syrupLibraryVersion")
    compileOnly("de.marhali:json5-java:3.0.0")
    compileOnly("com.electronwill.night-config:toml:3.8.3")
    val rosettaVersion = property("deps.rosetta") as String
    implementation("com.rasanovum.rosetta:rosetta-1.21.1-neoforge:$rosettaVersion")
    jarJar("com.rasanovum.rosetta:rosetta-1.21.1-neoforge:$rosettaVersion") {
        version {
            strictly("[$rosettaVersion,0.2.0)")
            prefer(rosettaVersion)
        }
    }
    compileOnly("top.theillusivec4.curios:curios-neoforge:${property("deps.curios")}:api") {
        isTransitive = false
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

neoForge {
    version = property("deps.neoforge") as String
    runs {
        create("client") {
            client()
            gameDirectory = rootProject.file("run")
        }
        create("server") { server(); gameDirectory = rootProject.file("run") }
    }
    mods.create(modId) { sourceSet(sourceSets.main.get()) }
}

tasks.processResources {
    val values = mapOf(
        "version" to project.version,
        "modId" to modId,
        "modName" to project.property("mod.name"),
        "modDescription" to project.property("mod.description"),
        "license" to project.property("mod.license"),
        "mc" to project.property("mod.neoforge_mc_range"),
        "members" to project.property("mod.authors"),
        "homepage" to project.property("mod.homepage"),
        "issues" to project.property("mod.issues"),
        "sources" to project.property("mod.sources"),
        "neoforge" to project.property("deps.neoforge"),
        "rosetta" to project.property("deps.rosetta"),
        "syrupLibrary" to project.property("deps.syrup_library"),
    )
    inputs.properties(values)
    exclude("fabric.mod.json")
    filesMatching("META-INF/neoforge.mods.toml") { expand(values) }
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
val uploadVersion = "${property("mod.version")}+$minecraftVersion-neoforge"
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
    file = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dryRun.set(publishDryRun)
    version = uploadVersion
    displayName = "${property("mod.name")} ${property("mod.version")} - NeoForge $minecraftVersion"
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText
    type = when (property("publish.release_type").toString().lowercase()) {
        "stable" -> STABLE
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> throw GradleException("publish.release_type must be stable, beta, or alpha")
    }
    modLoaders.add("neoforge")

    modrinth {
        projectId = modrinthId.ifBlank { "00000000" }
        accessToken = modrinthToken
        compatibleVersions.forEach { minecraftVersions.add(it) }
        environment = CLIENT_AND_SERVER
        requires("syrup-library")
    }
    curseforge {
        projectId = curseForgeId.ifBlank { "0" }
        accessToken = curseForgeToken
        compatibleVersions.forEach { minecraftVersions.add(it) }
        client = true
        server = true
        requires("syrup-library")
    }
}
