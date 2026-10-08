package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import okio.Path
import kotlin.test.fail

class Repository(val directory: Path) {
    val path: String = directory.toString()
    val url: String = "file://$directory"
    val head: String get() = git("rev-parse", "HEAD")

    init {
        FileSystem.SYSTEM.createDirectories(directory)
        git("init", "--quiet", "--initial-branch", "main")
    }

    fun program(message: String, name: String = directory.name, wrapper: Boolean = true): String {
        write("settings.gradle.kts", settingsScript(name))
        write("build.gradle.kts", buildScript("target.binaries.executable()"))
        write("src/nativeMain/kotlin/Main.kt", mainFunction("main", message))
        if (wrapper) wrapper()
        return commit()
    }

    fun programs(commands: Map<String, String>): String {
        val names = commands.keys.toList()
        write("settings.gradle.kts", settingsScript(directory.name))
        write(
            "build.gradle.kts",
            buildScript(
                names.indices.joinToString("\n        ") { index ->
                    "target.binaries.executable(\"command$index\") { baseName = \"${names[index]}\"; entryPoint = \"command$index\" }"
                },
            ),
        )
        write(
            "src/nativeMain/kotlin/Main.kt",
            names.indices.joinToString("\n") { index -> mainFunction("command$index", commands.getValue(names[index])) },
        )
        wrapper()
        return commit()
    }

    fun projects(commands: Map<String, String>, baseName: String? = null): String {
        write("settings.gradle.kts", settingsScript(directory.name, commands.keys))
        write("build.gradle.kts", "plugins {\n    $kotlinPlugin apply false\n}\n")
        commands.forEach { (name, message) ->
            val binary = if (baseName == null) "target.binaries.executable()" else "target.binaries.executable { baseName = \"$baseName\" }"
            write("$name/build.gradle.kts", buildScript(binary, "kotlin(\"multiplatform\")"))
            write("$name/src/nativeMain/kotlin/Main.kt", mainFunction("main", message))
        }
        wrapper()
        return commit()
    }

    fun wrapper() {
        listOf("gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties").forEach { file ->
            FileSystem.SYSTEM.createDirectories((directory / file).parent!!)
            FileSystem.SYSTEM.copy(project / file, directory / file)
        }
    }

    fun write(file: String, content: String) {
        FileSystem.SYSTEM.createDirectories((directory / file).parent!!)
        FileSystem.SYSTEM.write(directory / file) { writeUtf8(content) }
    }

    fun commit(): String {
        git("add", "--all")
        git("commit", "--quiet", "--message", "Change")
        return head
    }

    fun branch(name: String) {
        git("checkout", "--quiet", "-b", name)
    }

    fun checkout(name: String) {
        git("checkout", "--quiet", name)
    }

    fun submodule(source: Repository, directory: String) {
        git("-c", "protocol.file.allow=always", "submodule", "add", "--quiet", source.url, directory)
    }

    fun git(vararg arguments: String): String {
        val outcome = launch(
            listOf("git") + arguments,
            directory,
            mapOf(
                "GIT_CONFIG_GLOBAL" to "/dev/null",
                "GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_AUTHOR_NAME" to "kotlinstall",
                "GIT_AUTHOR_EMAIL" to "kotlinstall@example.com",
                "GIT_COMMITTER_NAME" to "kotlinstall",
                "GIT_COMMITTER_EMAIL" to "kotlinstall@example.com",
            ),
        )
        if (outcome.status != 0) fail("git ${arguments.joinToString(" ")} failed in $directory: $outcome")
        return outcome.output
    }
}

val localSubmodules = mapOf("GIT_CONFIG_COUNT" to "1", "GIT_CONFIG_KEY_0" to "protocol.file.allow", "GIT_CONFIG_VALUE_0" to "always")

val kotlinPlugin: String get() = "kotlin(\"multiplatform\") version \"${setting("KOTLINSTALL_TEST_KOTLIN_VERSION")}\""

fun settingsScript(name: String, projects: Collection<String> = emptyList()) = buildString {
    appendLine("rootProject.name = \"$name\"")
    appendLine()
    appendLine("dependencyResolutionManagement {")
    appendLine("    repositories {")
    appendLine("        mavenCentral()")
    appendLine("    }")
    appendLine("}")
    projects.forEach { appendLine("include(\"$it\")") }
}

fun buildScript(binaries: String, plugin: String = kotlinPlugin) = buildString {
    appendLine("plugins {")
    appendLine("    $plugin")
    appendLine("}")
    appendLine()
    appendLine("kotlin {")
    appendLine("    listOf(macosArm64(), macosX64(), linuxX64()).forEach { target ->")
    appendLine("        $binaries")
    appendLine("    }")
    appendLine("}")
}

fun mainFunction(name: String, message: String) = "fun $name() {\n    println(\"$message\")\n}\n"
