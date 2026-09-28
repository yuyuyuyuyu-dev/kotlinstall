package dev.yuyuyuyuyu.kotlinstall

import okio.Path

object KotlinToolchain {
    private val nativeApplications = setOf("linux/app", "macos/app")

    private data class Module(val name: String, val type: String, val platforms: Set<String>)

    fun isProject(directory: Path) = files.exists(directory / "project.yaml") || files.exists(directory / "module.yaml")

    fun build(project: Path, host: Host): List<Command> {
        val kotlin = cli(project)
        val modules = modules(capture(kotlin + listOf("show", "modules", "--fields=name,type,platforms"), project))
            .filter { it.type in nativeApplications && host.name in it.platforms }
            .map { it.name }
        if (modules.isEmpty()) return emptyList()
        execute(
            kotlin + "build" + modules.flatMap { listOf("--module", it) } +
                listOf("--platform", host.kotlinPlatform, "--variant", "release"),
            project,
        )
        val link = "link${host.kotlinPlatform.replaceFirstChar(Char::uppercaseChar)}Release"
        return modules.map { Command(it, output(project / "build" / "tasks", "_${it}_$link")) }
    }

    private fun cli(project: Path): List<String> {
        if (isRegularFile(project / "kotlin")) return listOf("sh", "kotlin")
        val version = runCatching { capture(listOf("kotlin", "--version"), showErrors = false) }.getOrDefault("")
        if ("Kotlin Toolchain" !in version) {
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

    private fun output(tasks: Path, prefix: String): Path {
        val directories = if (isDirectory(tasks)) files.list(tasks).filter { it.name.startsWith(prefix) } else emptyList()
        return directories.flatMap { files.list(it) }.firstOrNull { it.name.endsWith(".kexe") && isRegularFile(it) }
            ?: fail("Could not find the output of $prefix in $tasks")
    }
}
