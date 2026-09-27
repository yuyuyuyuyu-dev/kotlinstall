package dev.yuyuyuyuyu.kotlinstall

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.writeText

object Gradle {
    fun isProject(directory: Path) =
        listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle").any { directory.resolve(it).exists() }

    fun build(project: Path, work: Path, platforms: Set<Platform>, host: Host?): List<Command> {
        val script = work.resolve("kotlinstall.init.gradle")
        script.writeText(checkNotNull(javaClass.getResource("/kotlinstall.init.gradle")).readText())
        val manifest = work.resolve("gradle-commands.tsv")
        val gradle = if (project.resolve("gradlew").isRegularFile()) listOf("sh", "gradlew") else listOf("gradle")
        execute(
            gradle + listOf(
                "--no-daemon",
                "--init-script", script.toString(),
                "-Dorg.gradle.configuration-cache=false",
                "-Dorg.gradle.configureondemand=false",
                "-Pkotlinstall.manifest=$manifest",
                "-Pkotlinstall.platforms=${platforms.joinToString(",") { it.option }}",
                "-Pkotlinstall.host=${host?.konanTarget.orEmpty()}",
                "kotlinstall",
            ),
            project,
        )
        if (!manifest.isRegularFile()) fail("Gradle did not report the commands of the project")
        return manifest.readLines().filter { it.isNotBlank() }.map { it.split('\t') }.map { fields ->
            when (fields.first()) {
                "jvm" -> StartScript(Path(fields[1]), Path(fields[2]), fields[3].ifEmpty { null }?.let(::Path))
                else -> Path(fields[1]).let { NativeExecutable(it.nameWithoutExtension, it) }
            }
        }
    }
}
