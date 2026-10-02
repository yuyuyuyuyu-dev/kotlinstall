package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class Installer(private val home: Home, private val force: Boolean) {
    fun install(source: String, reference: Reference?) {
        val name = packageName(source)
        val host = Host.current() ?: fail("Kotlin/Native cannot build programs on this host")
        val installed = home.receipt(name)
        if (installed != null && !installed.isFrom(source) && !force) {
            fail("$name is already installed from ${installed.source}. Use --force to replace it.")
        }
        val work = createTemporaryDirectory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY, "kotlinstall-")
        try {
            val checkout = work / name
            announce("Getting the source code of $name")
            val revision = Git.clone(source, reference, checkout)
            if (installed != null && installed.isFrom(source) && installed.revision == revision && !force) {
                inform("$installed is up to date. Use --force to reinstall it.")
                return
            }
            if (!Gradle.isProject(checkout)) fail("$source is not a Gradle project")
            announce("Building $name")
            val commands = select(Gradle.build(checkout, work, host), host)
            val receipt = Receipt(name, source, reference, revision, commands.keys.sorted())
            announce("Installing $name")
            home.lock { place(receipt, commands) }
            report(receipt)
        } finally {
            files.deleteRecursively(work)
        }
    }

    private fun packageName(source: String): String {
        val name = source.substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")
        if (name.isEmpty() || name.startsWith(".") || !name.all { it.isLetterOrDigit() || it in "-_." }) {
            fail("Cannot name a package after $source")
        }
        return name
    }

    private fun select(commands: List<Command>, host: Host): Map<String, Command> {
        if (commands.isEmpty()) {
            fail("No Kotlin/Native executables for ${host.kotlinPlatform} were found. Only Kotlin/Native executables can be installed.")
        }
        return commands.groupBy { it.name }.mapValues { (name, candidates) ->
            if (name.isEmpty() || name.startsWith(".") || '/' in name) fail("Cannot install a command named $name")
            candidates.singleOrNull() ?: fail("More than one command is named $name")
        }
    }

    private fun place(receipt: Receipt, commands: Map<String, Command>) {
        val conflicts = home.conflicts(receipt.name, commands.keys).joinToString(", ") { command ->
            home.owner(command)?.let { "$command (installed by $it)" } ?: command
        }
        if (conflicts.isNotEmpty()) {
            if (!force) fail("${home.bin} already has $conflicts. Use --force to replace them.")
            inform("Replacing $conflicts")
        }
        val staging = home.stage(receipt.name)
        try {
            home.install(receipt, staging, commands.mapValues { (_, command) -> stage(staging, command) })
        } finally {
            files.deleteRecursively(staging)
        }
    }

    private fun stage(staging: Path, command: Command): Path {
        val file = "native".toPath() / command.name
        files.createDirectories(staging / "native")
        files.copy(command.file, staging / file)
        setMode(staging / file, "755")
        return file
    }

    private fun report(receipt: Receipt) {
        inform("Installed $receipt")
        receipt.commands.forEach { inform("    ${home.bin / it}") }
        if (environment("PATH").orEmpty().split(':').none { it.isNotEmpty() && absolute(it.toPath()) == home.bin }) {
            warn("${home.bin} is not in PATH. Add it to run the installed commands, for example: export PATH=\"${home.bin}:\$PATH\"")
        }
    }
}
