package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

class InstallCommand : CoreCliktCommand(name = "install") {
    override fun help(context: Context) = "Build a Kotlin program from a Git repository and install its commands"

    private val repository by argument("URL", help = "URL or path of the Git repository")

    private val reference by mutuallyExclusiveOptions(
        option("--branch", metavar = "BRANCH", help = "Branch to install").convert { Reference(Reference.Kind.BRANCH, it) },
        option("--tag", metavar = "TAG", help = "Tag to install").convert { Reference(Reference.Kind.TAG, it) },
        option("--rev", metavar = "REV", help = "Commit to install").convert { Reference(Reference.Kind.REV, it) },
    ).single()

    private val platform by option("--platform", help = "Install only the JVM or only the native commands")
        .choice(Platform.entries.associateBy { it.option })

    private val force by option("--force", help = "Reinstall even if up to date, and take over commands of other packages").flag()

    @OptIn(ExperimentalPathApi::class)
    override fun run() {
        val home = Home.current()
        val source = locate(repository)
        val name = source.substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")
        if (name.isEmpty() || name.startsWith(".") || !name.all { it.isLetterOrDigit() || it in "-_." }) {
            fail("Cannot name a package after $repository")
        }
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
                echo("$installed is already installed. Use --force to reinstall it.")
                return
            }
            val platforms = platform?.let(::setOf) ?: Platform.entries.toSet()
            val commands = select(
                when {
                    KotlinToolchain.isProject(checkout) -> KotlinToolchain.build(checkout, platforms, host)
                    Gradle.isProject(checkout) -> Gradle.build(checkout, work, platforms, host)
                    else -> fail("$source is neither a Gradle project nor a Kotlin Toolchain project")
                },
            )
            val receipt = Receipt(name, source, reference, platform, revision, commands.keys.sorted())
            home.lock {
                val conflicts = home.conflicts(name, commands.keys).joinToString(", ") { command ->
                    home.owner(command)?.let { "$command (installed by $it)" } ?: command
                }
                if (conflicts.isNotEmpty()) {
                    if (!force) fail("${home.bin} already has $conflicts. Use --force to replace them.")
                    echo("Replacing $conflicts")
                }
                val staging = home.stage(name)
                try {
                    val stager = Stager(staging, home.packages.resolve(name))
                    home.install(receipt, staging, commands.mapValues { (_, command) -> stager.add(command) })
                } finally {
                    staging.deleteRecursively()
                }
            }
            echo("Installed $receipt")
            receipt.commands.forEach { echo("    ${home.bin.resolve(it)}") }
            if (System.getenv("PATH").orEmpty().split(':').none { it.isNotEmpty() && Path(it).toAbsolutePath().normalize() == home.bin }) {
                warn("${home.bin} is not in PATH. Add it to PATH to run the installed commands.")
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun locate(repository: String): String {
        val path = Path(repository.trimEnd('/'))
        return if ("://" !in repository && path.isDirectory()) path.toAbsolutePath().normalize().pathString else repository.trimEnd('/')
    }

    private fun select(commands: List<Command>): Map<String, Command> {
        if (commands.isEmpty()) fail("No commands to install were found. Only applications and native executables can be installed.")
        return commands.groupBy { it.name }.mapValues { (name, candidates) ->
            val preferred = candidates.filter { it.platform == Platform.NATIVE }.ifEmpty { candidates }
            if (preferred.size > 1) fail("More than one command is named $name")
            if (preferred.size < candidates.size) {
                echo("Installing the native build of $name. Use --platform jvm to install the JVM build instead.")
            }
            preferred.single()
        }
    }
}
