package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.ERROR_ALREADY_EXISTS
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LOCKFILE_EXCLUSIVE_LOCK
import platform.windows.LockFileEx
import platform.windows.OPEN_ALWAYS
import platform.windows.OVERLAPPED
import kotlin.random.Random

private const val UNIQUE_LENGTH = 6
private val unique = ('A'..'Z') + ('a'..'z') + ('0'..'9')

@OptIn(ExperimentalForeignApi::class)
actual fun createTemporaryDirectory(
    parent: Path,
    prefix: String,
): Path {
    while (true) {
        val directory = parent / (prefix + (1..UNIQUE_LENGTH).map { unique.random(Random) }.joinToString(""))
        if (CreateDirectoryW(directory.toString(), null) != 0) return directory
        if (GetLastError() != ERROR_ALREADY_EXISTS.convert<UInt>()) {
            fail("Could not create a directory in $parent: ${lastError()}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun <T> withLock(
    file: Path,
    action: () -> T,
): T {
    val handle =
        CreateFileW(
            file.toString(),
            GENERIC_WRITE.convert(),
            (FILE_SHARE_READ or FILE_SHARE_WRITE).convert(),
            null,
            OPEN_ALWAYS.convert(),
            FILE_ATTRIBUTE_NORMAL.convert(),
            null,
        )
    if (handle == INVALID_HANDLE_VALUE) fail("Could not open $file: ${lastError()}")
    try {
        memScoped {
            val overlapped = alloc<OVERLAPPED>()
            val locked =
                LockFileEx(
                    handle,
                    LOCKFILE_EXCLUSIVE_LOCK.convert(),
                    0u,
                    UInt.MAX_VALUE,
                    UInt.MAX_VALUE,
                    overlapped.ptr,
                )
            if (locked == 0) fail("Could not lock $file: ${lastError()}")
        }
        return action()
    } finally {
        CloseHandle(handle)
    }
}
