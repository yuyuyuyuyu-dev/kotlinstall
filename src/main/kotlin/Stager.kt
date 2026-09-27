package dev.yuyuyuyuyu.kotlinstall

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

class Stager(private val staging: Path, private val destination: Path) {
    private val distributions = mutableMapOf<Path, Path>()

    fun add(command: Command): Path = when (command) {
        is StartScript -> addStartScript(command)
        is ExecutableJar -> addExecutableJar(command)
        is NativeExecutable -> addNativeExecutable(command)
    }

    private fun addStartScript(command: StartScript): Path {
        val distribution = distributions.getOrPut(command.distribution) {
            val directory = generateSequence(1) { it + 1 }
                .map { Path("jvm", if (it == 1) command.distribution.name else "${command.distribution.name}-$it") }
                .first { !staging.resolve(it).exists() }
            copyDirectory(command.distribution, staging.resolve(directory))
            directory
        }
        val script = distribution.resolve(command.script).normalize()
        if (!script.startsWith(distribution)) fail("Cannot install ${command.script} outside its distribution")
        val javaHome = command.javaHome?.let(Java::runtimeBuiltWith) ?: return script
        return launcher(
            command.name,
            $$"""
            #!/bin/sh
            if [ -x $${quote(javaHome.resolve("bin/java"))} ]; then
              JAVA_HOME=$${quote(javaHome)}
              export JAVA_HOME
            fi
            exec $${quote(destination.resolve(script))} "$@"
            """,
        )
    }

    private fun addExecutableJar(command: ExecutableJar): Path {
        val jar = Path("jvm", "${command.name}.jar")
        copyFile(command.jar, staging.resolve(jar))
        val java = Java.runtimeFor(command.name, command.jar)?.resolve("bin/java")
        return launcher(
            command.name,
            $$"""
            #!/bin/sh
            java=$${quote(java?.toString().orEmpty())}
            if [ ! -x "$java" ]; then
              if [ -n "$JAVA_HOME" ]; then
                java="$JAVA_HOME/bin/java"
              else
                java=java
              fi
            fi
            exec "$java" -jar $${quote(destination.resolve(jar))} "$@"
            """,
        )
    }

    private fun addNativeExecutable(command: NativeExecutable): Path {
        val file = Path("native", command.name)
        copyFile(command.file, staging.resolve(file))
        staging.resolve(file).setPosixFilePermissions(executable)
        return file
    }

    private fun launcher(name: String, script: String): Path {
        val file = Path("launchers", name)
        staging.resolve(file).also { it.parent.createDirectories() }.writeText(script.trimIndent() + "\n")
        staging.resolve(file).setPosixFilePermissions(executable)
        return file
    }

    private fun copyFile(source: Path, target: Path) {
        target.parent.createDirectories()
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
    }

    private fun copyDirectory(source: Path, target: Path) = Files.walk(source).use { paths ->
        paths.forEach { path ->
            val copy = target.resolve(source.relativize(path))
            if (path.isDirectory(LinkOption.NOFOLLOW_LINKS)) {
                copy.createDirectories()
            } else {
                Files.copy(path, copy, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
            }
        }
    }

    private fun quote(value: Any) = "'" + value.toString().replace("'", "'\\''") + "'"

    private companion object {
        val executable = PosixFilePermissions.fromString("rwxr-xr-x")
    }
}
