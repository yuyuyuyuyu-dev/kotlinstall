plugins {
    kotlin("multiplatform") version "2.4.20"
}

kotlin {
    listOf(macosArm64(), macosX64(), linuxX64()).forEach { target ->
        target.binaries.executable {
            entryPoint = "dev.yuyuyuyuyu.kotlinstall.main"
        }
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
