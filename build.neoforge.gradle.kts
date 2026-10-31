plugins {
    id("net.neoforged.moddev") version "2.0.147"
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
    val rosettaNetworkingVersion = property("deps.rosetta_networking") as String
    implementation("net.rasanovum.rosetta:rosetta-1.21.1-neoforge:$rosettaVersion")
    jarJar("net.rasanovum.rosetta:rosetta-1.21.1-neoforge:$rosettaVersion") {
        version {
            strictly("[$rosettaVersion,)")
            prefer(rosettaVersion)
        }
    }
    implementation("net.rasanovum.rosetta:rosetta-networking-1.21.1-neoforge:$rosettaNetworkingVersion")
    jarJar("net.rasanovum.rosetta:rosetta-networking-1.21.1-neoforge:$rosettaNetworkingVersion") {
        version {
            strictly("[$rosettaNetworkingVersion,)")
            prefer(rosettaNetworkingVersion)
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
        "rosettaNetworking" to project.property("deps.rosetta_networking"),
        "syrupLibrary" to project.property("deps.syrup_library"),
    )
    inputs.properties(values)
    exclude("fabric.mod.json")
    filesMatching("META-INF/neoforge.mods.toml") { expand(values) }
}
