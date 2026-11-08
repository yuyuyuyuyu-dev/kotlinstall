package dev.yuyuyuyuyu.kotlinstall

import okio.Path

actual fun gradle(project: Path): List<String> {
    val script = if (isRegularFile(project / "gradlew.bat")) "gradlew.bat" else "gradle"
    return listOf("cmd", "/c", script)
}
