package dev.yuyuyuyuyu.kotlinstall

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.changeDir
import okio.Path
import platform.posix.usleep
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

private const val POLLING_MICROSECONDS = 1000u

actual fun commandLine(command: List<String>) = command.joinToString(" ", transform = ::quote)

@OptIn(ExperimentalAtomicApi::class)
actual fun spawn(
    command: List<String>,
    directory: Path?,
    output: StringBuilder?,
): Int {
    val process =
        try {
            Process
                .Builder(command.first())
                .args(command.drop(1))
                .changeDir(directory?.toString()?.toFile())
                .stdin(if (output == null) Stdio.Inherit else Stdio.Null)
                .stdout(if (output == null) Stdio.Inherit else Stdio.Pipe)
                .stderr(if (output == null) Stdio.Inherit else Stdio.Pipe)
                .createProcess()
        } catch (e: IOException) {
            fail("Could not run ${command.first()}: ${e.message}")
        }
    try {
        if (output == null) return process.waitFor()
        val closed = AtomicInt(0)
        return process
            .stdoutFeed { line -> if (line == null) closed.incrementAndFetch() else output.appendLine(line) }
            .stderrFeed { line -> if (line == null) closed.incrementAndFetch() else printError(line) }
            .waitFor()
            .also { while (closed.load() < 2) usleep(POLLING_MICROSECONDS) }
    } finally {
        process.destroy()
    }
}

private fun quote(argument: String) =
    if (argument.isNotEmpty() &&
        argument.all { it.isLetterOrDigit() || it in "%+,-./:=@_" }
    ) {
        argument
    } else {
        "'" + argument.replace("'", "'\\''") + "'"
    }
