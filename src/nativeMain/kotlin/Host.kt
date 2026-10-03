package dev.yuyuyuyuyu.kotlinstall

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CpuArchitecture
import kotlin.native.OsFamily
import kotlin.native.Platform

enum class Host(val konanTarget: String, val kotlinPlatform: String) {
    MACOS_ARM64("macos_arm64", "macosArm64"),
    MACOS_X64("macos_x64", "macosX64"),
    LINUX_X64("linux_x64", "linuxX64");

    companion object {
        @OptIn(ExperimentalNativeApi::class)
        fun current(): Host? = when (Platform.osFamily to Platform.cpuArchitecture) {
            OsFamily.MACOSX to CpuArchitecture.ARM64 -> MACOS_ARM64
            OsFamily.MACOSX to CpuArchitecture.X64 -> MACOS_X64
            OsFamily.LINUX to CpuArchitecture.X64 -> LINUX_X64
            else -> null
        }
    }
}
