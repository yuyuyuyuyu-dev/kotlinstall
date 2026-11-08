package dev.yuyuyuyuyu.kotlinstall

import okio.Path

expect fun environment(name: String): String?

expect fun userHome(): Path
