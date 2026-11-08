package dev.yuyuyuyuyu.kotlinstall

import okio.Path

expect val executableSuffix: String

expect fun putCommand(
    command: Path,
    executable: Path,
)

expect fun isCommandOf(
    command: Path,
    executable: Path,
): Boolean
