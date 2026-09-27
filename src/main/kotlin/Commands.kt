package dev.yuyuyuyuyu.kotlinstall

import java.nio.file.Path
import kotlin.io.path.name

enum class Platform(val option: String) {
    JVM("jvm"),
    NATIVE("native"),
}

sealed interface Command {
    val name: String
    val platform: Platform
}

data class StartScript(val distribution: Path, val script: Path, val javaHome: Path?) : Command {
    override val name: String get() = script.name
    override val platform get() = Platform.JVM
}

data class ExecutableJar(override val name: String, val jar: Path) : Command {
    override val platform get() = Platform.JVM
}

data class NativeExecutable(override val name: String, val file: Path) : Command {
    override val platform get() = Platform.NATIVE
}
