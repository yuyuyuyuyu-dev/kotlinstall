package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class UpdateCommand : CoreCliktCommand(name = "update") {
    override val hiddenFromHelp = true

    override fun help(context: Context) = "Reinstall packages when their sources have new commits"

    private val names by argument(
        "PACKAGE",
        help = "Name of a package to update. All packages are updated when none is given",
    ).multiple()

    private val force by option(
        "--force",
        help = "Reinstall even if up to date, and take over commands of other packages",
    ).flag()

    override fun run() {
        val home = Home.current()
        val receipts =
            if (names.isEmpty()) {
                home.receipts()
            } else {
                names.distinct().map { home.receipt(it) ?: fail("$it is not installed") }
            }
        val failed =
            receipts.filterNot { receipt ->
                runCatching { Installer(home, force).install(receipt.source, receipt.reference) }
                    .onFailure { printError(if (it is CliktError) it.message.orEmpty() else "Error: ${it.message}") }
                    .isSuccess
            }
        if (failed.isNotEmpty()) fail("Could not update ${failed.joinToString(", ") { it.name }}")
    }
}
