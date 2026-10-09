package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple

class UninstallCommand : CoreCliktCommand(name = "uninstall") {
    override fun help(context: Context) = "Remove installed packages and their commands"

    private val names by argument("PACKAGE", help = "Name of the package to remove").multiple(required = true)

    override fun run() {
        val home = Home.current()
        home.lock {
            val receipts = names.distinct().map { name -> home.receipt(name) ?: fail("$name is not installed") }
            receipts.forEach { receipt ->
                home.uninstall(receipt).forEach { inform("Removed ${home.bin / it}") }
                inform("Uninstalled ${receipt.name}")
            }
        }
    }
}
