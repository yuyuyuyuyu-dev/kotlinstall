package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKStringFromUtf16
import okio.Path
import okio.Path.Companion.toPath
import platform.windows.GetEnvironmentVariableW
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
actual fun environment(name: String): String? =
    memScoped {
        val size = GetEnvironmentVariableW(name, null, 0u)
        if (size == 0u) return null
        val value = allocArray<WCHARVar>(size.toInt())
        if (GetEnvironmentVariableW(name, value, size) == 0u) return null
        value.toKStringFromUtf16().takeIf { it.isNotBlank() }
    }

actual fun userHome(): Path = (environment("USERPROFILE") ?: fail("Could not find the home directory")).toPath()
