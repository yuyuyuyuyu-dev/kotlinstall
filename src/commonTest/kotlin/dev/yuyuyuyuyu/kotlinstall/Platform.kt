package dev.yuyuyuyuyu.kotlinstall

import okio.Path

expect val homeVariable: String

expect val temporaryVariables: List<String>

expect val pathSeparator: String

expect val wrapperRun: String

expect val gradleRun: String

expect fun variable(name: String): String?

expect fun executable(name: String): String

expect fun url(directory: Path): String

expect fun pathExample(bin: Path): String

expect fun removeDirectory(directory: Path)

expect fun writeGradle(
    directory: Path,
    project: Path,
)

expect fun runInstaller(
    script: String,
    arguments: List<String>,
    directory: Path,
    environment: Map<String, String?>,
): Outcome

expect fun launch(
    command: List<String>,
    directory: Path,
    environment: Map<String, String?> = emptyMap(),
    input: String? = null,
): Outcome
