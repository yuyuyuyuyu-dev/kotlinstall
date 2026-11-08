package dev.yuyuyuyuyu.kotlinstall

import okio.Path

actual val executableSuffix = ""

actual fun putCommand(
    command: Path,
    executable: Path,
) = files.createSymlink(command, executable.relativeTo(command.parent!!))

actual fun isCommandOf(
    command: Path,
    executable: Path,
) = files.metadataOrNull(command)?.symlinkTarget?.let { command.parent!!.resolve(it, normalize = true) } == executable
