package dev.yuyuyuyuyu.kotlinstall

import okio.Path

actual fun gradle(project: Path) = listOf("cmd", "/c", if (isRegularFile(project / "gradlew.bat")) "gradlew.bat" else "gradle")
