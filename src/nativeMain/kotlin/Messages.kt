package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CliktError
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fputs
import platform.posix.stderr

fun inform(message: Any) = println(message)

fun announce(phase: String) {
    val title = "==> $phase"
    val rule = "=".repeat(title.length + 1)
    println("\n$rule\n$title\n$rule")
}

fun printError(message: String) = echo(message, trailingNewline = true, error = true)

@OptIn(ExperimentalForeignApi::class)
fun echo(message: Any?, trailingNewline: Boolean, error: Boolean) {
    val text = if (trailingNewline) "$message\n" else message.toString()
    if (error) fputs(text, stderr) else print(text)
}

fun fail(message: String): Nothing = throw CliktError("Error: $message")
