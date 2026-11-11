package dev.yuyuyuyuyu.kotlinstall

import okio.Path

actual val executableSuffix = ".exe"

actual fun putCommand(
    command: Path,
    executable: Path,
) = files.copy(executable, command)

actual fun isCommandOf(
    command: Path,
    executable: Path,
) = isRegularFile(command) &&
    isRegularFile(executable) &&
    files.metadata(command).size == files.metadata(executable).size &&
    files.read(command) { readByteString() } == files.read(executable) { readByteString() }
