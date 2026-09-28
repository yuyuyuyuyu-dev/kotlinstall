package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CliktError
import platform.posix.fputs
import platform.posix.stderr

fun inform(message: Any) = println(message)

fun warn(message: String) = printError("Warning: $message")

fun printError(message: String) {
    fputs("$message\n", stderr)
}

fun fail(message: String): Nothing = throw CliktError("Error: $message")
