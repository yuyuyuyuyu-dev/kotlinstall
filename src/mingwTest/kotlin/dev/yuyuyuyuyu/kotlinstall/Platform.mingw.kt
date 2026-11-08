package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import okio.FileSystem
import okio.Path
import platform.windows.GetEnvironmentVariableW
import platform.windows.WCHARVar

actual val homeVariable = "USERPROFILE"

actual val temporaryVariables = listOf("TMP", "TEMP")

actual val pathSeparator = ";"

actual val wrapperRun = "cmd /c gradlew.bat"

actual val gradleRun = "cmd /c gradle"

@OptIn(ExperimentalForeignApi::class)
actual fun variable(name: String): String? =
    memScoped {
        val size = GetEnvironmentVariableW(name, null, 0u)
        if (size == 0u) return null
        val value = allocArray<WCHARVar>(size.toInt())
        GetEnvironmentVariableW(name, value, size)
        value.toKStringFromUtf16()
    }

actual fun executable(name: String) = "$name.exe"

actual fun url(directory: Path) = "file:///${directory.toString().replace('\\', '/')}"

actual fun pathExample(bin: Path) = "\$env:Path = \"$bin;\$env:Path\""

actual fun writeGradle(
    directory: Path,
    project: Path,
) {
    FileSystem.SYSTEM.write(directory / "gradle.bat") {
        writeUtf8("@call \"${project / "gradlew.bat"}\" %*\r\n")
    }
}

actual fun runInstaller(
    script: String,
    arguments: List<String>,
    directory: Path,
    environment: Map<String, String?>,
): Outcome {
    val file = directory / "install-kotlinstall.main.kts"
    FileSystem.SYSTEM.write(file) { writeUtf8(script) }
    return launch(listOf("cmd", "/c", "kotlinr", file.toString()) + arguments, directory, environment)
}
