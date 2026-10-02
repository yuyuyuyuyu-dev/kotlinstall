import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform") version "2.4.20"
}

kotlin {
    val host = when (HostManager.host) {
        KonanTarget.MACOS_ARM64 -> macosArm64()
        KonanTarget.MACOS_X64 -> macosX64()
        KonanTarget.LINUX_X64 -> linuxX64()
        else -> error("Kotlin/Native cannot build kotlinstall on this host")
    }
    host.binaries.executable {
        entryPoint = "dev.yuyuyuyuyu.kotlinstall.main"
    }

    compilerOptions {
        optIn.addAll(
            "kotlin.concurrent.atomics.ExperimentalAtomicApi",
            "kotlin.experimental.ExperimentalNativeApi",
            "kotlinx.cinterop.ExperimentalForeignApi",
        )
    }

    sourceSets {
        nativeMain.dependencies {
            implementation("com.github.ajalt.clikt:clikt-core:5.1.0")
            implementation("com.squareup.okio:okio:3.18.2")
            implementation("io.matthewnelson.kmp-process:process:0.5.0")
        }
    }
}
