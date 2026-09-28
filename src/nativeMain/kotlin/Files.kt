package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.F_SETLKW
import platform.posix.F_WRLCK
import platform.posix.O_CREAT
import platform.posix.O_WRONLY
import platform.posix.SEEK_SET
import platform.posix.chmod
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.flock
import platform.posix.mkdtemp
import platform.posix.open
import platform.posix.strerror

val files = FileSystem.SYSTEM

fun absolute(path: Path): Path = if (path.isAbsolute) path.normalized() else (files.canonicalize(".".toPath()) / path).normalized()

fun isDirectory(path: Path) = runCatching { files.metadata(files.canonicalize(path)).isDirectory }.getOrDefault(false)

fun isRegularFile(path: Path) = runCatching { files.metadata(files.canonicalize(path)).isRegularFile }.getOrDefault(false)

fun createTemporaryDirectory(parent: Path, prefix: String): Path = memScoped {
    val template = (parent / "${prefix}XXXXXX").toString().cstr.getPointer(this)
    (mkdtemp(template) ?: fail("Could not create a directory in $parent: ${reason()}")).toKString().toPath()
}

fun setMode(path: Path, mode: String) {
    if (chmod(path.toString(), mode.toInt(8).convert()) != 0) fail("Could not change the permissions of $path: ${reason()}")
}

fun <T> withLock(file: Path, action: () -> T): T {
    val descriptor = open(file.toString(), O_CREAT or O_WRONLY, "644".toInt(8))
    if (descriptor < 0) fail("Could not open $file: ${reason()}")
    try {
        memScoped {
            val lock = alloc<flock>()
            lock.l_type = F_WRLCK.convert()
            lock.l_whence = SEEK_SET.convert()
            lock.l_start = 0
            lock.l_len = 0
            if (fcntl(descriptor, F_SETLKW, lock.ptr) != 0) fail("Could not lock $file: ${reason()}")
        }
        return action()
    } finally {
        close(descriptor)
    }
}

private fun reason() = strerror(errno)?.toKString()
