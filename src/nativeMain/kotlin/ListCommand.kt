package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context

class ListCommand : CoreCliktCommand(name = "list") {
    override fun help(context: Context) = "List the installed packages and their commands"

    override fun run() {
        val packages = Home.current().receipts().map { receipt ->
            """
            package:  ${receipt.name}
            commands: ${receipt.commands.joinToString(", ")}
            source:   ${listOfNotNull(receipt.source, receipt.reference).joinToString(" ")}
            revision: ${receipt.revision.take(7)}
            """.trimIndent()
        }
        if (packages.isNotEmpty()) inform(packages.joinToString("\n\n"))
    }
}
