package dev.yuyuyuyuyu.kotlinstall

import okio.IOException
import okio.Path

class Trash(
    private val directory: Path,
) {
    fun discard(file: Path) {
        try {
            files.delete(file)
        } catch (_: IOException) {
            files.createDirectories(directory)
            files.atomicMove(file, createTemporaryDirectory(directory, "${file.name}-") / file.name)
        }
    }

    fun empty() {
        if (!isDirectory(directory)) return
        files.list(directory).forEach { runCatching { deleteTree(it) } }
        runCatching { files.delete(directory) }
    }
}
