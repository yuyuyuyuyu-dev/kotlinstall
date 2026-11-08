package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.chmod
import platform.posix.errno
import platform.posix.strerror

val files = FileSystem.SYSTEM

const val OCTAL = 8

fun absolute(path: Path): Path = (if (path.isAbsolute) path else files.canonicalize(".".toPath()) / path).normalized()

fun isDirectory(path: Path) = metadata(path)?.isDirectory == true

fun isRegularFile(path: Path) = metadata(path)?.isRegularFile == true

private fun metadata(path: Path) = runCatching { files.metadata(files.canonicalize(path)) }.getOrNull()

expect fun createTemporaryDirectory(
    parent: Path,
    prefix: String,
): Path

@OptIn(ExperimentalForeignApi::class)
fun setMode(
    path: Path,
    mode: String,
) {
    if (chmod(path.toString(), mode.toInt(OCTAL).convert()) != 0) {
        fail("Could not change the permissions of $path: ${reason()}")
    }
}

expect fun <T> withLock(
    file: Path,
    action: () -> T,
): T

@OptIn(ExperimentalForeignApi::class)
fun reason() = strerror(errno)?.toKString()
