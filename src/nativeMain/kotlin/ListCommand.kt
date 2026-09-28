package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context

class ListCommand : CoreCliktCommand(name = "list") {
    override fun help(context: Context) = "List the installed packages and their commands"

    override fun run() {
        Home.current().receipts().forEach { receipt ->
            inform(receipt)
            receipt.commands.forEach { inform("    $it") }
        }
    }
}
