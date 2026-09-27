package dev.yuyuyuyuyu.kotlinstall

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
class Installer(private val home: Home, private val force: Boolean) {
    fun install(source: String, reference: Reference?, platform: Platform?) {
        val name = packageName(source)
        val host = Host.current()
        if (platform == Platform.NATIVE && host == null) fail("Native commands cannot be built on this host")
        val installed = home.receipt(name)
        if (installed != null && !installed.isFrom(source) && !force) {
            fail("$name is already installed from ${installed.source}. Use --force to replace it.")
        }
        val work = createTempDirectory("kotlinstall-")
        try {
            val checkout = work.resolve(name)
            val revision = Git.clone(source, reference, checkout)
            if (installed != null && installed.isFrom(source) && installed.revision == revision && installed.platform == platform && !force) {
                inform("$installed is up to date. Use --force to reinstall it.")
                return
            }
            val commands = select(build(source, checkout, work, platform, host))
            val receipt = Receipt(name, source, reference, platform, revision, commands.keys.sorted())
            home.lock { place(receipt, commands) }
            report(receipt)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun packageName(source: String): String {
        val name = source.substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")
        if (name.isEmpty() || name.startsWith(".") || !name.all { it.isLetterOrDigit() || it in "-_." }) {
            fail("Cannot name a package after $source")
        }
        return name
    }

    private fun build(source: String, checkout: Path, work: Path, platform: Platform?, host: Host?): List<Command> {
        val platforms = platform?.let(::setOf) ?: Platform.entries.toSet()
        return when {
            KotlinToolchain.isProject(checkout) -> KotlinToolchain.build(checkout, platforms, host)
            Gradle.isProject(checkout) -> Gradle.build(checkout, work, platforms, host)
            else -> fail("$source is neither a Gradle project nor a Kotlin Toolchain project")
        }
    }

    private fun select(commands: List<Command>): Map<String, Command> {
        if (commands.isEmpty()) fail("No commands to install were found. Only applications and native executables can be installed.")
        return commands.groupBy { it.name }.mapValues { (name, candidates) ->
            if (name.isEmpty() || name.startsWith(".") || '/' in name) fail("Cannot install a command named $name")
            val preferred = candidates.filter { it.platform == Platform.NATIVE }.ifEmpty { candidates }
            if (preferred.size > 1) fail("More than one command is named $name")
            if (preferred.size < candidates.size) {
                inform("Installing the native build of $name. Use --platform jvm to install the JVM build instead.")
            }
            preferred.single()
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
            val stager = Stager(staging, home.packages.resolve(receipt.name))
            home.install(receipt, staging, commands.mapValues { (_, command) -> stager.add(command) })
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun report(receipt: Receipt) {
        inform("Installed $receipt")
        receipt.commands.forEach { inform("    ${home.bin.resolve(it)}") }
        if (System.getenv("PATH").orEmpty().split(':').none { it.isNotEmpty() && Path(it).toAbsolutePath().normalize() == home.bin }) {
            warn("${home.bin} is not in PATH. Add it to run the installed commands, for example: export PATH=\"${home.bin}:\$PATH\"")
        }
    }
}
