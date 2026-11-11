package dev.yuyuyuyuyu.kotlinstall

import io.matthewnelson.kmp.process.Process
import okio.FileSystem
import okio.Path

actual val homeVariable = "HOME"

actual val temporaryVariables = listOf("TMPDIR")

actual val pathSeparator = ":"

actual val wrapperRun = "sh gradlew"

actual val gradleRun = "gradle"

actual fun variable(name: String): String? = Process.Current.environment()[name]

actual fun executable(name: String) = name

actual fun url(directory: Path) = "file://$directory"

actual fun pathExample(bin: Path) = "export PATH=\"$bin:\$PATH\""

actual fun removeDirectory(directory: Path) = FileSystem.SYSTEM.deleteRecursively(directory)

actual fun writeGradle(
    directory: Path,
    project: Path,
) {
    FileSystem.SYSTEM.write(directory / "gradle") {
        writeUtf8("#!/bin/sh\nexec sh '${project / "gradlew"}' \"\$@\"\n")
    }
    launch(listOf("chmod", "755", (directory / "gradle").toString()), directory)
}

actual fun runInstaller(
    script: String,
    arguments: List<String>,
    directory: Path,
    environment: Map<String, String?>,
): Outcome =
    launch(
        listOf("kotlinr", "-howtorun", ".main.kts", "/dev/stdin") + arguments,
        directory,
        environment,
        script,
    )
