package dev.yuyuyuyuyu.kotlinstall

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object KotlinToolchain {
    private val nativeApplications = setOf("linux/app", "macos/app")

    private data class Module(val name: String, val type: String, val platforms: Set<String>)

    fun isProject(directory: Path) = directory.resolve("project.yaml").exists() || directory.resolve("module.yaml").exists()

    fun build(project: Path, platforms: Set<Platform>, host: Host?): List<Command> {
        val kotlin = cli(project)
        val modules = modules(capture(kotlin + listOf("show", "modules", "--fields=name,type,platforms"), project))
        val tasks = project.resolve("build/tasks")
        val jvm = if (Platform.JVM in platforms) modules.filter { it.type == "jvm/app" }.map { it.name } else emptyList()
        if (jvm.isNotEmpty()) {
            execute(kotlin + "package" + jvm.flatMap { listOf("--module", it) } + listOf("--format", "executable-jar"), project)
        }
        val jars = jvm.map { ExecutableJar(it, output(tasks, "_${it}_executableJar", ".jar")) }
        if (Platform.NATIVE !in platforms || host == null) return jars
        val native = modules.filter { it.type in nativeApplications && host.name in it.platforms }.map { it.name }
        if (native.isNotEmpty()) {
            execute(
                kotlin + "build" + native.flatMap { listOf("--module", it) } +
                    listOf("--platform", host.kotlinPlatform, "--variant", "release"),
                project,
            )
        }
        val link = "link${host.kotlinPlatform.replaceFirstChar(Char::uppercaseChar)}Release"
        return jars + native.map { NativeExecutable(it, output(tasks, "_${it}_$link", ".kexe")) }
    }

    private fun cli(project: Path): List<String> {
        if (project.resolve("kotlin").isRegularFile()) return listOf("sh", "kotlin")
        val version = runCatching { capture(listOf("kotlin", "--version")) }.getOrDefault("")
        if (!version.startsWith("Kotlin Toolchain")) {
            fail("The project has no kotlin wrapper script, and the kotlin command is not the Kotlin Toolchain CLI")
        }
        return listOf("kotlin")
    }

    private fun modules(table: String): List<Module> {
        val rows = mutableListOf<List<String>>()
        table.lineSequence().map { it.trim() }.filter { it.startsWith("│") }.forEach { line ->
            val cells = line.removePrefix("│").removeSuffix("│").split("│").map { it.trim() }
            if (cells.size != 3) return@forEach
            if (cells.first().isEmpty() && rows.isNotEmpty()) {
                rows[rows.lastIndex] = rows.last().zip(cells) { head, tail -> "$head $tail".trim() }
            } else {
                rows += cells
            }
        }
        return rows.drop(1).map { (name, type, platforms) ->
            Module(name, type, platforms.split(',', ' ').filter { it.isNotBlank() }.toSet())
        }
    }

    private fun output(tasks: Path, prefix: String, extension: String): Path {
        val directories = if (tasks.isDirectory()) tasks.listDirectoryEntries().filter { it.name.startsWith(prefix) } else emptyList()
        return directories.flatMap { it.listDirectoryEntries() }.firstOrNull { it.name.endsWith(extension) && it.isRegularFile() }
            ?: fail("Could not find the output of $prefix in $tasks")
    }
}
