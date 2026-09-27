package dev.yuyuyuyuyu.kotlinstall

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines

object Java {
    private val home = Path(System.getProperty("java.home")).toRealPath()
    private val version = Runtime.version().feature()

    fun runtimeBuiltWith(javaHome: Path): Path? {
        val runtime = runCatching { javaHome.toRealPath() }.getOrNull() ?: return null
        if (runtime == home) return null
        val required = version(runtime) ?: return null
        return runtime.takeIf { version < required }
    }

    fun runtimeFor(command: String, jar: Path): Path? {
        val required = requiredVersion(jar) ?: return null
        if (version >= required) return null
        val runtime = kotlinToolchainRuntimes()
            .mapNotNull { runtime -> version(runtime)?.let { runtime to it } }
            .filter { (_, version) -> version >= required }
            .minByOrNull { (_, version) -> version }
            ?.first
        if (runtime == null) warn("$command needs Java $required or newer. Set JAVA_HOME to such a JDK to run it.")
        return runtime
    }

    private fun version(javaHome: Path): Int? {
        val release = javaHome.resolve("release")
        if (!release.isRegularFile()) return null
        val value = release.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") }?.substringAfter('=')?.trim('"')
        val numbers = value?.split('.', '_', '-', '+')?.map { it.toIntOrNull() } ?: return null
        return if (numbers.firstOrNull() == 1) numbers.getOrNull(1) else numbers.firstOrNull()
    }

    private fun requiredVersion(jar: Path): Int? = JarFile(jar.toFile()).use { file ->
        val attributes = file.manifest?.mainAttributes ?: return null
        val main = attributes.getValue("Start-Class") ?: attributes.getValue("Main-Class") ?: return null
        val path = main.replace('.', '/') + ".class"
        val entry = file.getJarEntry("BOOT-INF/classes/$path") ?: file.getJarEntry(path) ?: return null
        val header = file.getInputStream(entry).use { it.readNBytes(8) }
        if (header.size < 8) return null
        ((header[6].toInt() and 0xff) shl 8 or (header[7].toInt() and 0xff)) - 44
    }

    private fun kotlinToolchainRuntimes(): List<Path> {
        val cache = System.getenv("KOTLIN_SHARED_CACHE_DIR")?.takeIf { it.isNotBlank() }?.let(::Path) ?: kotlinToolchainCache()
        if (!cache.isDirectory()) return emptyList()
        return runCatching {
            Files.walk(cache, 6).use { paths ->
                paths.filter { it.name == "release" && it.resolveSibling("bin").resolve("java").isExecutable() }
                    .map { it.parent }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun kotlinToolchainCache(): Path {
        val user = Path(System.getProperty("user.home"))
        if (System.getProperty("os.name").startsWith("Mac")) return user.resolve("Library/Caches/JetBrains/Kotlin")
        val cache = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::Path) ?: user.resolve(".cache")
        return cache.resolve("JetBrains/Kotlin")
    }
}
