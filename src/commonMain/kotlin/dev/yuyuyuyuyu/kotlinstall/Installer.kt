package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class Installer(
    private val home: Home,
    private val force: Boolean,
) {
    fun install(
        source: String,
        subdirectory: String?,
        reference: Reference?,
    ) {
        val directory = directoryName(subdirectory ?: source)
        val host = Host.current() ?: fail("Kotlin/Native cannot build programs on this host")
        val previous = home.receipts().find { it.isFrom(source, subdirectory) }
        val work = createTemporaryDirectory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY, "kotlinstall-")
        try {
            val checkout = work / directory
            announce("Getting the source code of $directory")
            val revision = Git.clone(source, reference, checkout)
            if (!force && previous?.revision == revision) {
                inform("$previous is up to date")
                return
            }
            val project = subdirectory?.let { checkout / it } ?: checkout
            if (subdirectory != null && !isDirectory(project)) fail("$source has no directory $subdirectory")
            if (!Gradle.isProject(project)) {
                fail("${subdirectory?.let { "$it in " }.orEmpty()}$source is not a Gradle project")
            }
            announce("Building $directory")
            val build = Gradle.build(project, work, host)
            val name = packageName(build.project)
            val commands = select(build.commands, host)
            val receipt = Receipt(name, source, subdirectory, reference, revision, commands.keys.sorted())
            announce("Installing $name")
            home.lock { place(receipt, previous, commands) }
            report(receipt)
        } finally {
            files.deleteRecursively(work)
        }
    }

    private fun directoryName(source: String): String {
        val name = source.substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")
        if (!isPackageName(name)) fail("Cannot name a package after $source")
        return name
    }

    private fun packageName(project: String): String {
        if (!isPackageName(project)) fail("Cannot name a package after the Gradle project $project")
        return project
    }

    private fun isPackageName(name: String): Boolean {
        val plain = name.all { it.isLetterOrDigit() || it in "-_." }
        return name.isNotEmpty() && !name.startsWith(".") && plain
    }

    private fun select(
        commands: List<Command>,
        host: Host,
    ): Map<String, Command> {
        if (commands.isEmpty()) {
            fail(
                "No Kotlin/Native executables for ${host.kotlinPlatform} were found. " +
                    "Only Kotlin/Native executables can be installed.",
            )
        }
        return commands.groupBy { it.name }.mapValues { (name, candidates) ->
            if (name.isEmpty() || name.startsWith(".") || '/' in name) fail("Cannot install a command named $name")
            candidates.singleOrNull() ?: fail("More than one command is named $name")
        }
    }

    private fun place(
        receipt: Receipt,
        previous: Receipt?,
        commands: Map<String, Command>,
    ) {
        val installed = home.receipt(receipt.name)
        if (!force && installed != null && !installed.isFrom(receipt.source, receipt.subdirectory)) {
            fail("${receipt.name} is already installed from ${installed.origin}")
        }
        val conflicts =
            home
                .conflicts(receipt.name, commands.keys)
                .filterNot { previous != null && home.owner(it) == previous.name }
                .joinToString(", ") { command ->
                    home.owner(command)?.let { "$command (installed by $it)" } ?: command
                }
        if (conflicts.isNotEmpty()) {
            if (!force) fail("${home.bin} already has $conflicts")
            inform("Replacing $conflicts")
        }
        val staging = home.stage(receipt.name)
        try {
            home.install(receipt, staging, commands.mapValues { (_, command) -> stage(staging, command) })
            if (previous != null && previous.name != receipt.name) home.receipt(previous.name)?.let(home::uninstall)
        } finally {
            files.deleteRecursively(staging)
        }
    }

    private fun stage(
        staging: Path,
        command: Command,
    ): Path {
        val file = "native".toPath() / command.name
        files.createDirectories(staging / "native")
        files.copy(command.file, staging / file)
        setMode(staging / file, "755")
        return file
    }

    private fun report(receipt: Receipt) {
        inform("Installed $receipt")
        receipt.commands.forEach { inform("    ${home.bin / it}") }
    }
}
