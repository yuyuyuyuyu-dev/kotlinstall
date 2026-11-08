package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.fail

data class Outcome(
    val status: Int,
    val output: String,
    val error: String,
)

fun listed(
    name: String,
    revision: String,
    source: String,
    vararg commands: String,
) = "package:  $name\ncommands: ${commands.joinToString(", ")}\nsource:   $source\nrevision: ${revision.take(7)}"

class Sandbox {
    val root: Path =
        FileSystem.SYSTEM.run {
            val suffix = Random.nextLong().toULong().toString(36)
            val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kotlinstall-test-$suffix"
            createDirectories(directory, mustCreate = true)
            canonicalize(directory)
        }
    val home: Path = root / "home"
    val bin: Path = home / "bin"
    val temporary: Path = root / "temporary"

    private val defaults =
        mapOf("KOTLINSTALL_HOME" to home.toString()) +
            temporaryVariables.associateWith { temporary.toString() } +
            mapOf(
                // kotlinstall builds and installs programs written in Kotlin, so the tests have to build many times.
                // We could not put up with that without the build cache.
                "GRADLE_OPTS" to
                    listOfNotNull(variable("GRADLE_OPTS"), "-Dorg.gradle.caching=true")
                        .joinToString(" "),
            )

    init {
        FileSystem.SYSTEM.createDirectories(temporary)
    }

    fun kotlinstall(
        vararg arguments: String,
        directory: Path = root,
        environment: Map<String, String?> = emptyMap(),
    ): Outcome = launch(listOf(setting("KOTLINSTALL_TEST_EXECUTABLE")) + arguments, directory, defaults + environment)

    fun install(vararg arguments: String) {
        val outcome = kotlinstall("install", *arguments)
        if (outcome.status != 0) fail("Could not install ${arguments.joinToString(" ")}: $outcome")
    }

    fun installer(
        vararg arguments: String,
        environment: Map<String, String?> = emptyMap(),
    ): Outcome =
        runInstaller(
            FileSystem.SYSTEM.read(project / "install-kotlinstall.main.kts") { readUtf8() },
            arguments.toList(),
            root,
            defaults + environment,
        )

    fun command(
        name: String,
        vararg arguments: String,
    ): Outcome = launch(listOf((bin / executable(name)).toString()) + arguments, root, defaults)

    fun repository(
        name: String,
        parent: String = "repositories",
    ): Repository = Repository(root / parent / name)

    fun source(): Repository {
        val repository = repository("kotlinstall")
        launch(listOf("git", "ls-files", "--cached", "--others", "--exclude-standard"), project)
            .output
            .lines()
            .filter { FileSystem.SYSTEM.metadataOrNull(project / it)?.isRegularFile == true }
            .forEach { file ->
                FileSystem.SYSTEM.createDirectories((repository.directory / file).parent!!)
                FileSystem.SYSTEM.copy(project / file, repository.directory / file)
            }
        repository.commit()
        return repository
    }

    fun gradle(): Path {
        val directory = root / "gradle"
        FileSystem.SYSTEM.createDirectories(directory)
        writeGradle(directory, project)
        return directory
    }

    fun temporaryFiles(): List<String> =
        FileSystem.SYSTEM
            .list(temporary)
            .map { it.name }
            .filter { it.startsWith("kotlinstall-") }

    fun delete() = FileSystem.SYSTEM.deleteRecursively(root)
}

val project: Path get() = setting("KOTLINSTALL_TEST_PROJECT").toPath()

fun setting(name: String): String = variable(name) ?: fail("$name is not set")

fun buildCaches(): Map<String, String> {
    val home = setting(homeVariable).toPath()
    return mapOf(
        "GRADLE_USER_HOME" to (variable("GRADLE_USER_HOME") ?: (home / ".gradle").toString()),
        "KONAN_DATA_DIR" to (variable("KONAN_DATA_DIR") ?: (home / ".konan").toString()),
    )
}
