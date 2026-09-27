package dev.yuyuyuyuyu.kotlinstall

import java.io.IOException
import java.nio.file.Path

fun execute(command: List<String>, directory: Path? = null) {
    val process = start(ProcessBuilder(command).inheritIO(), directory)
    verify(command, process.waitFor())
}

fun capture(
    command: List<String>,
    directory: Path? = null,
    errors: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT,
): String {
    val process = start(ProcessBuilder(command).redirectError(errors), directory)
    process.outputStream.close()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    verify(command, process.waitFor())
    return output
}

private fun start(builder: ProcessBuilder, directory: Path?): Process {
    directory?.let { builder.directory(it.toFile()) }
    return try {
        builder.start()
    } catch (e: IOException) {
        fail("Could not run ${builder.command().first()}: ${e.message}")
    }
}

private fun verify(command: List<String>, status: Int) {
    if (status != 0) fail("${command.joinToString(" ")} failed with exit code $status")
}
