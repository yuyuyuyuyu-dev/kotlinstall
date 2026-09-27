package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlin.io.path.name
import kotlin.io.path.toPath

class UpdateCommand : CoreCliktCommand(name = "update") {
    override fun help(context: Context) = "Reinstall packages when their sources have new commits"

    private val names by argument("PACKAGE", help = "Name of a package to update. All packages are updated when none is given").multiple()

    private val force by option("--force", help = "Reinstall even if up to date, and take over commands of other packages").flag()

    override fun run() {
        val home = Home.current()
        val receipts = if (names.isEmpty()) home.receipts() else names.distinct().map { home.receipt(it) ?: fail("$it is not installed") }
        val running = running(home)
        val failed = receipts.sortedBy { it.name == running }.filterNot { receipt ->
            runCatching { Installer(home, force).install(receipt.source, receipt.reference, receipt.platform) }
                .onFailure { System.err.println(if (it is CliktError) it.message else "Error: ${it.message}") }
                .isSuccess
        }
        if (failed.isNotEmpty()) fail("Could not update ${failed.joinToString(", ") { it.name }}")
    }

    private fun running(home: Home): String? {
        val code = runCatching { javaClass.protectionDomain.codeSource.location.toURI().toPath().toRealPath() }.getOrNull()
        val packages = runCatching { home.packages.toRealPath() }.getOrNull()
        if (code == null || packages == null || !code.startsWith(packages) || code == packages) return null
        return packages.relativize(code).first().name
    }
}
