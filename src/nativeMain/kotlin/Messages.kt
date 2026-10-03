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

@OptIn(ExperimentalForeignApi::class)
fun printError(message: String) {
    fputs("$message\n", stderr)
}

fun fail(message: String): Nothing = throw CliktError("Error: $message")
