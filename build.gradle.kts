@file:Suppress("UnstableApiUsage")

import net.fabricmc.loom.api.LoomGradleExtensionAPI

val buildConfigName: String by project
val license: String by project
val minecraftVersion: String by project
val modVersion: String by project
val modPackage: String by project
val modId: String by project
val modName: String by project
val modAuthor: String by project
val modDescription: String by project
val modCredits: String by project
val parchmentVersion: String by project
val forgeVersion: String by project
val cctVersion: String by project
val jeiVersion: String by project
val githubUser: String by project
val githubRepo: String by project

plugins {
    id("dev.architectury.loom") version "1.3.+"
    id("io.github.juuxel.loom-vineflower") version "1.11.0"
    id("com.github.gmazzo.buildconfig") version "4.0.4"
    java
    `maven-publish`
}

base {
    version = "$minecraftVersion-$modVersion"
    archivesName.set("$modId-forge")
}

loom {
    silentMojangMappingsLicense()

    if (project.findProperty("enableAccessWidener") == "true") {
        accessWidenerPath.set(file("src/main/resources/$modId.accesswidener"))
        forge {
            convertAccessWideners.set(true)
            extraAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
        }
        println("Access widener enabled for project. Access widener path: ${loom.accessWidenerPath.get()}")
    }
}

repositories {
    maven("https://maven.parchmentmc.org") // Parchment
    maven("https://maven.minecraftforge.net") // Forge artifacts
    mavenLocal()
}

dependencies {
    // Minecraft
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-$minecraftVersion:$parchmentVersion@zip")
    })

    // Forge
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Use the exact installed DeepSpace CC:Tweaked API for compile-only integration.
    modCompileOnly(files("C:/Users/henry/AppData/Roaming/PolyMC/instances/Deepspace v1.0.3/.minecraft/mods/cc-tweaked-1.20.1-forge-1.120.2.jar"))

    // GTCEu is optional at runtime; local JAR is compile-only for the DeepSpace fork.
    modCompileOnly(files("C:/Users/henry/AppData/Roaming/PolyMC/instances/Deepspace v1.0.3/.minecraft/mods/gtceu-1.20.1-8.0.0.jar"))
}

tasks {
    register<JavaExec>("verifyGTMeasurementMath") {
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.github.almostreliable.energymeter.compat.gtceu.GTMeasurementMathTest")
    }
    register<JavaExec>("verifyGTTransferRouter") {
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.github.almostreliable.energymeter.compat.gtceu.GTTransferRouterTest")
    }
    named("check") { dependsOn("verifyGTMeasurementMath", "verifyGTTransferRouter") }
    processResources {
        val resourceTargets = listOf("META-INF/mods.toml", "pack.mcmeta")

        val replaceProperties = mapOf(
            "license" to license,
            "minecraftVersion" to minecraftVersion,
            "version" to project.version as String,
            "modId" to modId,
            "modName" to modName,
            "modAuthor" to modAuthor,
            "modDescription" to modDescription,
            "modCredits" to modCredits,
            "forgeVersion" to forgeVersion,
            // use major version for FML only because wrong Forge version error message
            // is way better than FML error message
            "forgeFMLVersion" to forgeVersion.substringBefore("."),
            "cctVersion" to cctVersion,
            "githubUser" to githubUser,
            "githubRepo" to githubRepo
        )

        println("[Process Resources] Replacing resource properties: ")
        replaceProperties.forEach { (key, value) -> println("\t -> $key = $value") }

        inputs.properties(replaceProperties)
        filesMatching(resourceTargets) {
            expand(replaceProperties)
        }
    }


    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    withType<GenerateModuleMetadata> {
        enabled = false
    }
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

extensions.configure<LoomGradleExtensionAPI> {
    runs {
        forEach {
            val dir = "run/${it.environment}"
            println("[Run Config] '${it.name}' directory: $dir")
            it.runDir(dir)
            // allows DCEVM hot-swapping when using the JBR (https://github.com/JetBrains/JetBrainsRuntime)
            it.vmArgs("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AllowEnhancedClassRedefinition")
        }
    }
}

buildConfig {
    buildConfigField("String", "MOD_ID", "\"$modId\"")
    buildConfigField("String", "MOD_NAME", "\"$modName\"")
    buildConfigField("String", "MOD_VERSION", "\"$version\"")
    packageName(modPackage)
    className(buildConfigName)
    useJavaOutput()
}
