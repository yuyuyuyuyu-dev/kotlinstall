package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import okio.Path.Companion.toPath

class InstallCommand : CoreCliktCommand(name = "install") {
    override fun help(context: Context) = "Build a Kotlin/Native program from a Git repository and install its commands"

    private val repository by argument("URL", help = "URL or path of the Git repository")

    private val reference by mutuallyExclusiveOptions(
        option("--branch", metavar = "BRANCH", help = "Branch to install").convert { Reference(Reference.Kind.BRANCH, it) },
        option("--tag", metavar = "TAG", help = "Tag to install").convert { Reference(Reference.Kind.TAG, it) },
        option("--rev", metavar = "REV", help = "Commit to install").convert { Reference(Reference.Kind.REV, it) },
    ).single()

    private val force by option("--force", help = "Reinstall even if up to date, and take over commands of other packages").flag()

    override fun run() = Installer(Home.current(), force).install(locate(repository), reference)

    private fun locate(repository: String): String {
        val path = repository.trimEnd('/').toPath()
        return if ("://" !in repository && isDirectory(path)) absolute(path).toString() else repository.trimEnd('/')
    }
}
