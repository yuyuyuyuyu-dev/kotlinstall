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

fun main(args: Array<String>) {
    val commands = listOf(InstallCommand(), UpdateCommand(), UninstallCommand(), ListCommand())
    val hidden = commands.filter { it.hiddenFromHelp }.map { it.commandName }.toSet()
    Kotlinstall()
        .subcommands(commands)
        .context {
            exitProcess = { status -> kotlin.system.exitProcess(status) }
            echoMessage = { _, message, trailingNewline, error -> echo(message, trailingNewline, error) }
            val suggest = suggestTypoCorrection
            suggestTypoCorrection = { entered, candidates -> suggest(entered, candidates - hidden) }
        }
        .main(args)
}
