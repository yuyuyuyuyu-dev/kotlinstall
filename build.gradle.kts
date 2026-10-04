import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
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

    sourceSets {
        nativeMain.dependencies {
            implementation("com.github.ajalt.clikt:clikt-core:5.1.0")
            implementation("com.squareup.okio:okio:3.18.2")
            implementation("io.matthewnelson.kmp-process:process:0.5.0")
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    val kotlinstall = host.binaries.getExecutable(NativeBuildType.RELEASE)
    tasks.withType<KotlinNativeTest>().configureEach {
        dependsOn(kotlinstall.linkTaskProvider)
        environment("KOTLINSTALL_TEST_EXECUTABLE", kotlinstall.outputFile.path)
        environment("KOTLINSTALL_TEST_PROJECT", projectDir.path)
        environment("KOTLINSTALL_TEST_KOTLIN_VERSION", getKotlinPluginVersion())
        outputs.upToDateWhen { false }
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
