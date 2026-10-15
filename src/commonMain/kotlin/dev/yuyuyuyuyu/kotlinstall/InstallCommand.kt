package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import okio.Path.Companion.toPath

class InstallCommand : CoreCliktCommand(name = "install") {
    override fun help(context: Context) =
        "Build a Kotlin/Native Gradle project from a Git repository " +
            "and install its commands"

    private val repository by argument("URL", help = "URL or path of the Git repository")

    private val subdirectory by option(
        "--subdirectory",
        metavar = "SUBDIRECTORY",
        help = "Directory of the Gradle project, relative to the root of the repository",
    ).convert { value ->
        val segments = value.trimEnd('/').split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("$value is not a directory in the repository")
        }
        segments.joinToString("/")
    }

    private val reference by mutuallyExclusiveOptions(
        option("--branch", metavar = "BRANCH", help = "Branch to install", hidden = true)
            .convert { Reference(Reference.Kind.BRANCH, it) },
        option("--tag", metavar = "TAG", help = "Tag to install", hidden = true)
            .convert { Reference(Reference.Kind.TAG, it) },
        option("--rev", metavar = "REV", help = "Commit to install", hidden = true)
            .convert { Reference(Reference.Kind.REV, it) },
    ).single()

    private val force by option(
        "--force",
        help = "Reinstall even if up to date, and take over commands of other packages",
        hidden = true,
    ).flag()

    override fun run() = Installer(Home.current(), force).install(locate(repository), subdirectory, reference)

    private fun locate(repository: String): String {
        val path = repository.trimEnd('/').toPath()
        return if ("://" !in repository && isDirectory(path)) absolute(path).toString() else repository.trimEnd('/')
    }
}
