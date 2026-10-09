import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.version.catalog.update)
}

detekt {
    source.setFrom("src")
}

kotlin {
    val host =
        when (HostManager.host) {
            KonanTarget.MACOS_ARM64 -> macosArm64()
            KonanTarget.MACOS_X64 -> macosX64()
            KonanTarget.LINUX_X64 -> linuxX64()
            else -> error("Kotlin/Native cannot build kotlinstall on this host")
        }
    host.binaries.executable {
        entryPoint = "dev.yuyuyuyuyu.kotlinstall.main"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.clikt.core)
            implementation(libs.okio)
            implementation(libs.kmp.process)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
