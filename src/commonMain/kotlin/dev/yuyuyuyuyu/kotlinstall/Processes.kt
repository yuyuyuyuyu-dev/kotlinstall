package dev.yuyuyuyuyu.kotlinstall

import okio.Path

fun execute(
    command: List<String>,
    directory: Path? = null,
) {
    inform("\nRUN: `${commandLine(command)}`")
    verify(command, spawn(command, directory, null))
}

fun capture(
    command: List<String>,
    directory: Path? = null,
): String {
    inform("\nRUN: `${commandLine(command)}`")
    val output = StringBuilder()
    val status = spawn(command, directory, output)
    print(output)
    verify(command, status)
    return output.toString()
}

expect fun commandLine(command: List<String>): String

expect fun spawn(
    command: List<String>,
    directory: Path?,
    output: StringBuilder?,
): Int

private fun verify(
    command: List<String>,
    status: Int,
) {
    if (status != 0) fail("${command.joinToString(" ")} failed with exit code $status")
}
