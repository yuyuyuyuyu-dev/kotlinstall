package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.getenv
import platform.posix.getpwuid
import platform.posix.getuid

@OptIn(ExperimentalForeignApi::class)
fun environment(name: String): String? = getenv(name)?.toKString()?.takeIf { it.isNotBlank() }

@OptIn(ExperimentalForeignApi::class)
fun userHome(): Path =
    (environment("HOME") ?: getpwuid(getuid())?.pointed?.pw_dir?.toKString() ?: fail("Could not find the home directory")).toPath()
