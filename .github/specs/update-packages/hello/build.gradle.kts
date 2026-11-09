plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    listOf(macosArm64(), linuxX64(), mingwX64()).forEach { target ->
        target.binaries.executable()
    }
}
