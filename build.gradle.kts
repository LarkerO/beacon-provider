import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import kotlin.math.max
import org.gradle.api.file.RegularFile
import org.gradle.api.GradleException

plugins {
    id("dev.architectury.loom") version "1.6.422" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("base")
}

val architecturyVersion: String by project
val architecturyVersion1165: String by project
val architecturyRelocationBase = "com.hydroline.beacon.shaded.architectury"
val checkoutsDir = layout.projectDirectory.dir("checkouts")

val architecturyJarFileName = "architectury-$architecturyVersion.jar"
val architecturyJar = layout.projectDirectory.file("checkouts/$architecturyJarFileName")
val downloadArchitecturyJar = tasks.register("downloadArchitecturyJar") {
    outputs.file(architecturyJar)
    doLast {
        val targetFile = architecturyJar.asFile
        checkoutsDir.asFile.mkdirs()
        if (!targetFile.exists() || targetFile.length() == 0L) {
            val baseUrl = "https://maven.architectury.dev/dev/architectury/architectury"
            val url = "$baseUrl/$architecturyVersion/$architecturyJarFileName"
            logger.lifecycle("Downloading Architectury API jar from $url")
            java.net.URL(url).openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            logger.lifecycle("Saved Architectury API jar to ${targetFile.absolutePath}")
        } else {
            logger.lifecycle("${targetFile.name} already exists; skipping download")
        }
    }
}

val architecturyJarFileName1165 = "architectury-$architecturyVersion1165.jar"
val architecturyJar1165 = layout.projectDirectory.file("checkouts/$architecturyJarFileName1165")
val downloadArchitecturyJar1165 = tasks.register("downloadArchitecturyJar1165") {
    outputs.file(architecturyJar1165)
    doLast {
        val targetFile = architecturyJar1165.asFile
        checkoutsDir.asFile.mkdirs()
        if (!targetFile.exists() || targetFile.length() == 0L) {
            val baseUrl = "https://maven.architectury.dev/me/shedaniel/architectury/architectury"
            val url = "$baseUrl/$architecturyVersion1165/$architecturyJarFileName1165"
            logger.lifecycle("Downloading Architectury API jar from $url")
            java.net.URL(url).openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            logger.lifecycle("Saved Architectury API jar to ${targetFile.absolutePath}")
        } else {
            logger.lifecycle("${targetFile.name} already exists; skipping download")
        }
    }
}

data class McTarget(
    val minecraftVersion: String,
    val forgeVersion: String,
    val fabricLoaderVersion: String,
    val fabricApiVersion: String,
    val javaVersion: Int,
    val packFormat: Int
)

enum class LoaderType {
    FABRIC,
    FORGE
}

data class LoaderProject(
    val name: String,
    val loader: LoaderType,
    val target: McTarget
)

val supportedTargets = mapOf(
    "1.16.5" to McTarget(
        minecraftVersion = "1.16.5",
        forgeVersion = "1.16.5-36.2.39",
        fabricLoaderVersion = "0.14.23",
        fabricApiVersion = "0.42.0+1.16",
        javaVersion = 8,
        packFormat = 6
    ),
    "1.18.2" to McTarget(
        minecraftVersion = "1.18.2",
        forgeVersion = "1.18.2-40.2.21",
        fabricLoaderVersion = "0.14.23",
        fabricApiVersion = "0.76.0+1.18.2",
        javaVersion = 17,
        packFormat = 8
    ),
    "1.20.1" to McTarget(
        minecraftVersion = "1.20.1",
        forgeVersion = "1.20.1-47.1.3",
        fabricLoaderVersion = "0.15.10",
        fabricApiVersion = "0.92.1+1.20.1",
        javaVersion = 17,
        packFormat = 15
    )
)

val createVersion_1_18_2: String by project
val createVersion_1_20_1: String by project
val createVersionMap = mapOf(
    "1.18.2" to createVersion_1_18_2,
    "1.20.1" to createVersion_1_20_1
)
val createJarMap = mapOf(
    "1.18.2" to layout.projectDirectory.file("libs/create/create-1.18.2-0.5.1.f.jar")
)

val mtrLibDir = layout.projectDirectory.dir("libs/mtr3")
val mtrJarNameMap = mapOf(
    "1.16.5" to mapOf(
        LoaderType.FABRIC to "MTR-fabric-1.16.5-3.2.2-hotfix-1-slim.jar",
        LoaderType.FORGE to "MTR-forge-1.16.5-3.2.2-hotfix-1-slim.jar"
    ),
    "1.18.2" to mapOf(
        LoaderType.FABRIC to "MTR-fabric-1.18.2-3.2.2-hotfix-1-slim.jar",
        LoaderType.FORGE to "MTR-forge-1.18.2-3.2.2-hotfix-1-slim.jar"
    ),
    "1.20.1" to mapOf(
        LoaderType.FABRIC to "MTR-fabric-1.20.1-3.2.2-hotfix-1-slim.jar",
        LoaderType.FORGE to "MTR-forge-1.20.1-3.2.2-hotfix-2-slim.jar"
    )
)
val mtrJarMap: Map<String, Map<LoaderType, RegularFile>> = mtrJarNameMap.mapValues { (_, loaders) ->
    loaders.mapValues { (_, fileName) ->
        mtrLibDir.file(fileName)
    }
}

fun resolveMtrJar(version: String, loader: LoaderType): RegularFile {
    val loaderMap = mtrJarMap[version]
        ?: throw GradleException("No MTR mapping for Minecraft $version; add a jar under libs/mtr3/")
    val jarFile = loaderMap[loader]
        ?: throw GradleException("No MTR jar for loader $loader on MC $version in libs/mtr3/")
    if (!jarFile.asFile.exists()) {
        throw GradleException("Expected MTR jar ${jarFile.asFile.relativeTo(layout.projectDirectory.asFile)} to exist; please download it.")
    }
    return jarFile
}

val loaderProjects = listOf(
    LoaderProject("fabric-1.16.5", LoaderType.FABRIC, supportedTargets.getValue("1.16.5")),
    LoaderProject("fabric-1.18.2", LoaderType.FABRIC, supportedTargets.getValue("1.18.2")),
    LoaderProject("fabric-1.20.1", LoaderType.FABRIC, supportedTargets.getValue("1.20.1")),
    LoaderProject("forge-1.16.5", LoaderType.FORGE, supportedTargets.getValue("1.16.5")),
    LoaderProject("forge-1.18.2", LoaderType.FORGE, supportedTargets.getValue("1.18.2")),
    LoaderProject("forge-1.20.1", LoaderType.FORGE, supportedTargets.getValue("1.20.1"))
)

subprojects {
    group = property("mavenGroup") as String
    version = property("modVersion") as String

    repositories {
        mavenLocal()
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.createmod.net")
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

loaderProjects.forEach { loaderProject ->
    project(":${loaderProject.name}") {
        configureLoaderProject(loaderProject)
    }
}

val targetBuildTasks = supportedTargets.mapValues { (version, _) ->
    val taskName = "buildTarget_${version.replace('.', '_')}"
    tasks.register(taskName) {
        group = "build"
        description = "Build Forge and Fabric variants for Minecraft $version"
        val targets = loaderProjects
            .filter { it.target.minecraftVersion == version }
            .map { ":${it.name}:build" }
        dependsOn(targets)
    }
}

val buildAllTargets = tasks.register("buildAllTargets") {
    group = "build"
    description = "Build every supported Minecraft/loader combination"
    dependsOn(targetBuildTasks.values)
}

tasks.named("build") {
    dependsOn(buildAllTargets)
}

fun Project.configureLoaderProject(config: LoaderProject) {
    evaluationDependsOn(":common")
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "com.github.johnrengelman.shadow")

    val architecturyApiConfiguration = configurations.maybeCreate("architecturyApi")

    val archivesBaseName = rootProject.property("archivesBaseName") as String
    base.archivesName.set("$archivesBaseName-${config.loader.name.lowercase()}-${config.target.minecraftVersion}")

    val compileLanguageVersion = max(config.target.javaVersion, 17)
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension>("java") {
        toolchain.languageVersion.set(JavaLanguageVersion.of(compileLanguageVersion))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(config.target.javaVersion)
    }

    val loomExtension = extensions.getByType<LoomGradleExtensionAPI>()
    loomExtension.silentMojangMappingsLicense()
    if (config.loader == LoaderType.FORGE) {
        loomExtension.forge {
            convertAccessWideners.set(false)
        }
    }

    val commonProject = project(":common")
    val commonSources = commonProject.extensions.getByType(SourceSetContainer::class.java)
        .getByName("main").output

    dependencies {
        add("minecraft", "com.mojang:minecraft:${config.target.minecraftVersion}")
        add("mappings", loomExtension.officialMojangMappings())
        add("implementation", commonProject)
        if (config.target.minecraftVersion == "1.16.5") {
            add("architecturyApi", files(architecturyJar1165).builtBy(downloadArchitecturyJar1165))
        } else {
            add("architecturyApi", files(architecturyJar).builtBy(downloadArchitecturyJar))
        }
        add("compileOnly", files(resolveMtrJar(config.target.minecraftVersion, config.loader)))
    }

    tasks.withType<Jar>().configureEach {
        dependsOn(commonProject.tasks.named("classes"))
        from(commonSources)
    }

    val shadowJar = tasks.named<ShadowJar>("shadowJar") {
        archiveClassifier.set("shadow")
        configurations = listOf(architecturyApiConfiguration)
        if (config.target.minecraftVersion == "1.16.5") {
            relocate("me.shedaniel.architectury", architecturyRelocationBase)
            relocate("dev.architectury", architecturyRelocationBase)
        } else {
            relocate("dev.architectury", architecturyRelocationBase)
        }
        exclude("architectury.accessWidener")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true
    }

    val architecturyContentsDir = layout.buildDirectory.dir("architecturyContents/${project.name}")
    val architecturyContentsCopy = tasks.register<Copy>("architecturyContents${project.name}") {
        dependsOn(shadowJar)
        from(provider { zipTree(shadowJar.get().archiveFile) })
        into(architecturyContentsDir)
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val relocationPackage = architecturyRelocationBase.replace('.', '/')

        filesMatching("architectury-common.mixins.json") {
            filter {
                var content = it.replace("dev.architectury.mixin", "$architecturyRelocationBase.mixin")
                if (config.target.minecraftVersion == "1.16.5") {
                    content = content.replace("\"compatibilityLevel\": \"JAVA_16\"", "\"compatibilityLevel\": \"JAVA_8\"")
                        .replace("\"compatibilityLevel\": \"JAVA_17\"", "\"compatibilityLevel\": \"JAVA_8\"")
                        .replace("me.shedaniel.architectury.mixin", "$architecturyRelocationBase.mixin")
                }
                content
            }
        }

        listOf("architectury.common.json", "architectury-common-refmap.json").forEach { fileName ->
            filesMatching(fileName) {
                filter {
                    var content = it.replace("dev/architectury", relocationPackage)
                    if (config.target.minecraftVersion == "1.16.5") {
                        content = content.replace("me/shedaniel/architectury", relocationPackage)
                    }
                    content
                }
            }
        }
    }

    tasks.named<RemapJarTask>("remapJar") {
        dependsOn(architecturyContentsCopy)
        from(architecturyContentsDir)
    }

    tasks.withType<RemapJarTask>().configureEach {
        isZip64 = true
    }

    when (config.loader) {
        LoaderType.FABRIC -> configureFabricProject(config)
        LoaderType.FORGE -> configureForgeProject(config)
    }
}

fun Project.configureFabricProject(config: LoaderProject) {
    dependencies {
        add("modImplementation", "net.fabricmc:fabric-loader:${config.target.fabricLoaderVersion}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${config.target.fabricApiVersion}")
    }

    configurations.maybeCreate("developmentFabric").extendsFrom(configurations.getByName("runtimeClasspath"))

    tasks.named<ProcessResources>("processResources") {
        inputs.property("version", version)
        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to version))
        }
    }
}

fun Project.configureForgeProject(config: LoaderProject) {
    dependencies {
        add("forge", "net.minecraftforge:forge:${config.target.forgeVersion}")
        val mcVersion = config.target.minecraftVersion
        val localJar = createJarMap[mcVersion]
        if (localJar != null) {
            if (!localJar.asFile.exists()) {
                throw GradleException("请把 Create 1.18.2 版本 jar (create-1.18.2-0.5.1.f.jar) 放到 libs/")
            }
            add("modImplementation", files(localJar))
        } else {
            createVersionMap[mcVersion]?.let { createVersion ->
                add(
                    "modImplementation",
                    "com.simibubi.create:create-${mcVersion}:$createVersion:slim"
                )
            }
        }
    }

    configurations.maybeCreate("developmentForge").extendsFrom(configurations.getByName("runtimeClasspath"))

    tasks.named<ProcessResources>("processResources") {
        inputs.property("version", version)
        filesMatching("META-INF/mods.toml") {
            expand(mapOf("version" to version))
        }
    }
}
