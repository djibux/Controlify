plugins {
    java

    alias(libs.plugins.loom)
    alias(libs.plugins.loom.quiltflower)

    alias(libs.plugins.minotaur)
    alias(libs.plugins.cursegradle)
    alias(libs.plugins.github.release)
    alias(libs.plugins.machete)
    alias(libs.plugins.grgit)
    `maven-publish`
}

group = "dev.isxander"
version = "1.0.0+1.19.4"

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com")
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.isxander.dev/snapshots")
    maven("https://maven.quiltmc.org/repository/release")
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://jitpack.io")
}

val minecraftVersion = libs.versions.minecraft.get()

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        mappings("org.quiltmc:quilt-mappings:$minecraftVersion+build.${libs.versions.quilt.mappings.get()}:intermediary-v2")
        officialMojangMappings()
    })
    modImplementation(libs.fabric.loader)

    modImplementation(libs.fabric.api)
    modImplementation(libs.yet.another.config.lib)
    modImplementation(libs.mod.menu)

    implementation(libs.mixin.extras)
    annotationProcessor(libs.mixin.extras)
    include(libs.mixin.extras)

    implementation(libs.hid4java)
    include(libs.hid4java)
}

machete {

}

tasks {
    processResources {
        val modId: String by project
        val modName: String by project
        val modDescription: String by project
        val githubProject: String by project

        inputs.property("id", modId)
        inputs.property("group", project.group)
        inputs.property("name", modName)
        inputs.property("description", modDescription)
        inputs.property("version", project.version)
        inputs.property("github", githubProject)

        filesMatching(listOf("fabric.mod.json", "quilt.mod.json")) {
            expand(
                "id" to modId,
                "group" to project.group,
                "name" to modName,
                "description" to modDescription,
                "version" to project.version,
                "github" to githubProject,
            )
        }
    }
    
    remapJar {
        archiveClassifier.set("fabric-$minecraftVersion")   
    }
    
    remapSourcesJar {
        archiveClassifier.set("fabric-$minecraftVersion-sources")   
    }

    register("releaseMod") {
        group = "mod"

        dependsOn("modrinth")
        dependsOn("modrinthSyncBody")
        dependsOn("curseforge")
        dependsOn("publish")
        dependsOn("githubRelease")
    }
}

java {
    withSourcesJar()   
}

val changelogText = file("changelogs/${project.version}.md").takeIf { it.exists() }?.readText() ?: "No changelog provided."

val modrinthId: String by project
if (modrinthId.isNotEmpty()) {
    modrinth {
        token.set(findProperty("modrinth.token")?.toString())
        projectId.set(modrinthId)
        versionNumber.set("${project.version}")
        versionType.set("release")
        uploadFile.set(tasks["remapJar"])
        gameVersions.set(listOf("1.19.3"))
        loaders.set(listOf("fabric", "quilt"))
        changelog.set(changelogText)
        syncBodyFrom.set(file("README.md").readText())
    }
}

val curseforgeId: String by project
if (hasProperty("curseforge.token") && curseforgeId.isNotEmpty()) {
    curseforge {
        apiKey = findProperty("curseforge.token")
        project(closureOf<me.hypherionmc.cursegradle.CurseProject> {
            mainArtifact(tasks["remapJar"], closureOf<me.hypherionmc.cursegradle.CurseArtifact> {
                displayName = "${project.version}"
            })

            id = curseforgeId
            releaseType = "release"
            addGameVersion("1.19.3")
            addGameVersion("Fabric")
            addGameVersion("Java 17")

            changelog = changelogText
            changelogType = "markdown"
        })

        options(closureOf<me.hypherionmc.cursegradle.Options> {
            forgeGradleIntegration = false
        })
    }
}

githubRelease {
    token(findProperty("github.token")?.toString())

    val githubProject: String by project
    val split = githubProject.split("/")
    owner(split[0])
    repo(split[1])
    tagName("${project.version}")
    targetCommitish("1.19.x/dev")
    body(changelogText)
    releaseAssets(tasks["remapJar"].outputs.files)
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            groupId = "dev.isxander"
            artifactId = base.archivesName.get()

            from(components["java"])
        }
    }

    repositories {
        val username = "XANDER_MAVEN_USER".let { System.getenv(it) ?: findProperty(it) }?.toString()
        val password = "XANDER_MAVEN_PASS".let { System.getenv(it) ?: findProperty(it) }?.toString()
        if (username != null && password != null) {
            maven(url = "https://maven.isxander.dev/releases") {
                name = "XanderReleases"
                credentials {
                    this.username = username
                    this.password = password
                }
            }
        } else {
            println("Xander Maven credentials not satisfied.")
        }
    }
}
