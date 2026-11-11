package dev.yuyuyuyuyu.kotlinstall

import okio.Path

actual fun gradle(project: Path) = if (isRegularFile(project / "gradlew")) listOf("sh", "gradlew") else listOf("gradle")
