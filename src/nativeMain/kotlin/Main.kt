package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class Kotlinstall : CoreCliktCommand(name = "kotlinstall") {
    override fun help(context: Context) = "Install Kotlin/Native programs from Git repositories"

    override fun run() = Unit
}

fun main(args: Array<String>) = Kotlinstall()
    .subcommands(InstallCommand(), UpdateCommand(), UninstallCommand(), ListCommand())
    .context {
        exitProcess = { status -> kotlin.system.exitProcess(status) }
        echoMessage = { _, message, trailingNewline, error -> echo(message, trailingNewline, error) }
    }
    .main(args)
