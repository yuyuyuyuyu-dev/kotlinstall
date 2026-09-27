package dev.yuyuyuyuyu.kotlinstall

import com.github.ajalt.clikt.core.CliktError

fun inform(message: Any) = println(message)

fun warn(message: String) = System.err.println("Warning: $message")

fun fail(message: String): Nothing = throw CliktError("Error: $message")
