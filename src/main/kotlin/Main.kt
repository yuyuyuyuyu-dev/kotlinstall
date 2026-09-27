package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class Kotlinstall : CoreCliktCommand(name = "kotlinstall") {
    override fun help(context: Context) = "Install Kotlin programs from Git repositories"

    override fun run() {
        if (System.getProperty("os.name").startsWith("Windows")) fail("Windows is not supported")
    }
}

fun main(args: Array<String>) = Kotlinstall()
    .subcommands(InstallCommand(), ListCommand(), UninstallCommand())
    .context { exitProcess = { status -> kotlin.system.exitProcess(status) } }
    .main(args)
