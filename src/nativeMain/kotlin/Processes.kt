package dev.yuyuyuyuyu.kotlinstall

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.changeDir
import okio.Path
import platform.posix.usleep
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

fun execute(command: List<String>, directory: Path? = null) {
    val process = start(command, directory, Stdio.Inherit, Stdio.Inherit)
    val status = try {
        process.waitFor()
    } finally {
        process.destroy()
    }
    verify(command, status)
}

fun capture(command: List<String>, directory: Path? = null): String {
    val output = StringBuilder()
    val closed = AtomicInt(0)
    val process = start(command, directory, Stdio.Null, Stdio.Pipe)
    val status = try {
        process.stdoutFeed { line -> if (line == null) closed.incrementAndFetch() else output.appendLine(line) }
            .stderrFeed { line -> if (line == null) closed.incrementAndFetch() else printError(line) }
            .waitFor()
            .also { while (closed.load() < 2) usleep(1000u) }
    } finally {
        process.destroy()
    }
    verify(command, status)
    return output.toString()
}

private fun start(command: List<String>, directory: Path?, input: Stdio, output: Stdio): Process =
    try {
        Process.Builder(command.first())
            .args(command.drop(1))
            .changeDir(directory?.toString()?.toFile())
            .stdin(input)
            .stdout(output)
            .stderr(output)
            .createProcess()
    } catch (e: Exception) {
        fail("Could not run ${command.first()}: ${e.message}")
    }

private fun verify(command: List<String>, status: Int) {
    if (status != 0) fail("${command.joinToString(" ")} failed with exit code $status")
}
