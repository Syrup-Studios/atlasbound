plugins {
    id("dev.kikugie.loom-back-compat")
}

val minecraftVersion = stonecutter.current.version
val modId = property("mod.id") as String
version = "${property("mod.version")}+$minecraftVersion-fabric"
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
    val rosetta = "net.rasanovum.rosetta:rosetta-1.21.1-fabric:${property("deps.rosetta")}" 
    val rosettaNetworking = "net.rasanovum.rosetta:rosetta-networking-1.21.1-fabric:${property("deps.rosetta_networking")}" 
    modImplementation(rosetta)
    include(rosetta)
    modImplementation(rosettaNetworking)
    include(rosettaNetworking)
    val mapFoundationJarPath = providers.gradleProperty("mapFoundationFabricJar")
        .orElse("map-foundation/versions/1.21.1-fabric/build/libs/map-foundation-1.21.1-fabric-0.1.1-atlasbound.3.jar").get()
    val mapFoundationJar = rootProject.file(mapFoundationJarPath)
    if (!mapFoundationJar.isFile) {
        throw GradleException("Map Foundation Fabric jar not found: ${mapFoundationJar.path}. Build the map-foundation project first, or set -PmapFoundationFabricJar=<path>.")
    }
    modImplementation(files(mapFoundationJar))
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
        "rosettaNetworking" to project.property("deps.rosetta_networking"),
        "mapFoundation" to project.property("deps.map_foundation"),
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
