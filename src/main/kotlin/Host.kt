package dev.yuyuyuyuyu.kotlinstall

enum class Host(val konanTarget: String, val kotlinPlatform: String) {
    MACOS_ARM64("macos_arm64", "macosArm64"),
    MACOS_X64("macos_x64", "macosX64"),
    LINUX_ARM64("linux_arm64", "linuxArm64"),
    LINUX_X64("linux_x64", "linuxX64");

    companion object {
        fun current(): Host? {
            val os = System.getProperty("os.name")
            val arm = when (System.getProperty("os.arch")) {
                "aarch64", "arm64" -> true
                "x86_64", "amd64" -> false
                else -> return null
            }
            return when {
                os.startsWith("Mac") -> if (arm) MACOS_ARM64 else MACOS_X64
                os.startsWith("Linux") -> if (arm) LINUX_ARM64 else LINUX_X64
                else -> null
            }
        }
    }
}
