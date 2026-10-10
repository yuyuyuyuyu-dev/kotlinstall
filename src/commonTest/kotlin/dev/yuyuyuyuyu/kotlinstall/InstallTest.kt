package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallTest {
    private val sandbox = Sandbox()

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should install the command of a Kotlin Native program`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(
            outcome.output,
            "Installed hello ${revision.take(7)} (${repository.url})\n    ${sandbox.bin}/hello",
        )
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should show each step and each command that it runs`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertContains(
            outcome.output,
            Regex(
                "==> Getting the source code of hello\n.*RUN: `git clone .*" +
                    "==> Building hello\n.*RUN: `sh gradlew .*" +
                    "==> Installing hello\n",
                RegexOption.DOT_MATCHES_ALL,
            ),
        )
    }

    @Test
    fun `should install from a relative path and remember the absolute path`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")

        // Act
        val outcome = sandbox.kotlinstall("install", "hello", directory = repository.directory.parent!!)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, listed("hello", revision, repository.path, "hello"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should name the package after the repository without the git suffix`() {
        // Arrange
        val repository = sandbox.repository("hello.git")
        val revision = repository.program("Hello!", name = "hello")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, listed("hello", revision, repository.url, "hello"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should ignore a slash at the end of the repository URL`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")

        // Act
        val outcome = sandbox.kotlinstall("install", "${repository.url}/")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, listed("hello", revision, repository.url, "hello"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should install the given branch`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("main")
        repository.branch("next")
        val revision = repository.program("next")
        repository.checkout("main")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, "--branch", "next")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "next", ""), sandbox.command("hello"))
        assertEquals(
            Outcome(0, listed("hello", revision, "${repository.url} --branch next", "hello"), ""),
            sandbox.kotlinstall("list"),
        )
    }

    @Test
    fun `should install the given revision`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("first")
        repository.program("second")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, "--rev", revision)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "first", ""), sandbox.command("hello"))
        assertEquals(
            Outcome(0, listed("hello", revision, "${repository.url} --rev $revision", "hello"), ""),
            sandbox.kotlinstall("list"),
        )
    }

    @Test
    fun `should refuse more than one of branch and tag and revision`() {
        // Act
        val url = "file://${sandbox.root}/hello"
        val outcome = sandbox.kotlinstall("install", url, "--branch", "next", "--rev", "0000000")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, "Error: option --branch cannot be used with --tag or --rev")
    }

    @Test
    fun `should leave an up-to-date package as it is`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output.lines(), "hello ${revision.take(7)} (${repository.url}) is up to date")
        assertFalse("Installed hello" in outcome.output, "$outcome")
    }

    @Test
    fun `should reinstall an up-to-date package with --force`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "Installed hello ${revision.take(7)} (${repository.url})")
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should reinstall a package whose source has new commits`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("first")
        sandbox.install(repository.url)
        val revision = repository.program("second")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "second", ""), sandbox.command("hello"))
        assertEquals(Outcome(0, listed("hello", revision, repository.url, "hello"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should remove the commands that the new version no longer has`() {
        // Arrange
        val repository = sandbox.repository("tools")
        repository.programs(mapOf("first" to "1", "second" to "2"))
        sandbox.install(repository.url)
        val revision = repository.programs(mapOf("first" to "1"))

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertFalse(FileSystem.SYSTEM.exists(sandbox.bin / "second"))
        assertEquals(Outcome(0, listed("tools", revision, repository.url, "first"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should refuse a package of the same name from another source`() {
        // Arrange
        val first = sandbox.repository("hello", parent = "first")
        first.program("first")
        sandbox.install(first.url)
        val second = sandbox.repository("hello", parent = "second")
        second.program("second")

        // Act
        val outcome = sandbox.kotlinstall("install", second.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error.lines(), "Error: hello is already installed from ${first.url}")
        assertEquals(Outcome(0, "first", ""), sandbox.command("hello"))
    }

    @Test
    fun `should replace a package of the same name from another source with --force`() {
        // Arrange
        val first = sandbox.repository("hello", parent = "first")
        first.program("first")
        sandbox.install(first.url)
        val second = sandbox.repository("hello", parent = "second")
        val revision = second.program("second")

        // Act
        val outcome = sandbox.kotlinstall("install", second.url, "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "second", ""), sandbox.command("hello"))
        assertEquals(Outcome(0, listed("hello", revision, second.url, "hello"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should refuse to take over a command of another package`() {
        // Arrange
        val first = sandbox.repository("first")
        val revision = first.programs(mapOf("shared" to "first"))
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        second.programs(mapOf("shared" to "second"))

        // Act
        val outcome = sandbox.kotlinstall("install", second.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error.lines(), "Error: ${sandbox.bin} already has shared (installed by first)")
        assertEquals(Outcome(0, "first", ""), sandbox.command("shared"))
        assertEquals(Outcome(0, listed("first", revision, first.url, "shared"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should remove another package when --force takes over its only command`() {
        // Arrange
        val first = sandbox.repository("first")
        first.programs(mapOf("shared" to "first"))
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        val revision = second.programs(mapOf("shared" to "second"))

        // Act
        val outcome = sandbox.kotlinstall("install", second.url, "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "Replacing shared (installed by first)")
        assertEquals(Outcome(0, "second", ""), sandbox.command("shared"))
        assertEquals(Outcome(0, listed("second", revision, second.url, "shared"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should keep the other commands of a package when --force takes over one of them`() {
        // Arrange
        val first = sandbox.repository("first")
        val firstRevision = first.programs(mapOf("own" to "own", "shared" to "first"))
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        val secondRevision = second.programs(mapOf("shared" to "second"))

        // Act
        val outcome = sandbox.kotlinstall("install", second.url, "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "own", ""), sandbox.command("own"))
        assertEquals(Outcome(0, "second", ""), sandbox.command("shared"))
        assertEquals(
            Outcome(
                0,
                listOf(
                    listed("first", firstRevision, first.url, "own"),
                    listed("second", secondRevision, second.url, "shared"),
                ).joinToString("\n\n"),
                "",
            ),
            sandbox.kotlinstall("list"),
        )
    }

    @Test
    fun `should refuse to replace a file that it did not install`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        FileSystem.SYSTEM.createDirectories(sandbox.bin)
        FileSystem.SYSTEM.write(sandbox.bin / "hello") { writeUtf8("mine") }

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error.lines(), "Error: ${sandbox.bin} already has hello")
        assertEquals("mine", FileSystem.SYSTEM.read(sandbox.bin / "hello") { readUtf8() })
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should replace a file that it did not install with --force`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        FileSystem.SYSTEM.createDirectories(sandbox.bin)
        FileSystem.SYSTEM.write(sandbox.bin / "hello") { writeUtf8("mine") }

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "Replacing hello\n")
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should refuse to replace a directory even with --force`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        FileSystem.SYSTEM.createDirectories(sandbox.bin / "hello")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, "--force")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, "Error: Cannot replace the directory ${sandbox.bin}/hello")
        assertTrue(FileSystem.SYSTEM.metadata(sandbox.bin / "hello").isDirectory)
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should refuse a repository that is not a Gradle project`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.write("README.md", "# hello\n")
        repository.commit()

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, "Error: ${repository.url} is not a Gradle project")
    }

    @Test
    fun `should refuse a program that is not built with Kotlin Native`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.write("settings.gradle.kts", settingsScript("hello"))
        repository.write(
            "build.gradle.kts",
            "plugins {\n    kotlin(\"jvm\") version \"${setting("KOTLINSTALL_TEST_KOTLIN_VERSION")}\"\n" +
                "    application\n}\n\n" +
                "application {\n    mainClass = \"MainKt\"\n}\n",
        )
        repository.write("src/main/kotlin/Main.kt", mainFunction("main", "Hello!"))
        repository.wrapper()
        repository.commit()

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(
            outcome.error,
            Regex(
                "Error: No Kotlin/Native executables for \\w+ were found\\. " +
                    "Only Kotlin/Native executables can be installed\\.",
            ),
        )
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should install the executables of every project in the build`() {
        // Arrange
        val repository = sandbox.repository("tools")
        val revision = repository.projects(mapOf("first" to "1", "second" to "2"))

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "1", ""), sandbox.command("first"))
        assertEquals(Outcome(0, "2", ""), sandbox.command("second"))
        assertEquals(
            Outcome(0, listed("tools", revision, repository.url, "first", "second"), ""),
            sandbox.kotlinstall("list"),
        )
    }

    @Test
    fun `should refuse projects that give the same name to their commands`() {
        // Arrange
        val repository = sandbox.repository("tools")
        repository.projects(mapOf("first" to "1", "second" to "2"), baseName = "same")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, "Error: More than one command is named same")
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should build with gradle from PATH when the project has no Gradle wrapper`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!", wrapper = false)
        val path = "${sandbox.gradle()}:${setting("PATH")}"

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, environment = mapOf("PATH" to path))

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "RUN: `gradle ")
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should install nothing when the build fails`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        repository.write("src/nativeMain/kotlin/Main.kt", "fun main() {\n    missing()\n}\n")
        repository.commit()

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, Regex("Error: sh gradlew .* failed with exit code 1"))
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should fail when the repository cannot be cloned`() {
        // Act
        val outcome = sandbox.kotlinstall("install", "file://${sandbox.root}/missing")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, Regex("Error: git clone .* failed with exit code 128"))
    }

    @Test
    fun `should refuse a repository that cannot name a package`() {
        // Act
        val outcome = sandbox.kotlinstall("install", "file://${sandbox.root}/.hidden")

        // Assert
        assertEquals(Outcome(1, "", "Error: Cannot name a package after file://${sandbox.root}/.hidden"), outcome)
    }

    @Test
    fun `should remove its temporary files after installing`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "${sandbox.temporary}/kotlinstall-")
        assertEquals(emptyList(), sandbox.temporaryFiles())
    }

    @Test
    fun `should remove its temporary files after failing`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.write("README.md", "# hello\n")
        repository.commit()

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url)

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.output, "${sandbox.temporary}/kotlinstall-")
        assertEquals(emptyList(), sandbox.temporaryFiles())
    }

    @Test
    fun `should install into the kotlinstall directory of the home directory by default`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        val user = sandbox.root / "user"
        FileSystem.SYSTEM.createDirectories(user)

        // Act
        val outcome =
            sandbox.kotlinstall(
                "install",
                repository.url,
                environment = buildCaches() + mapOf("KOTLINSTALL_HOME" to null, "HOME" to user.toString()),
            )

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "Hello!", ""), launch(listOf("$user/.kotlinstall/bin/hello"), sandbox.root))
    }

    @Test
    fun `should install a project whose sources are in a Git submodule`() {
        // Arrange
        val repository = programWithSubmodule()

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, environment = localSubmodules)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should install the given revision of a project whose sources are in a Git submodule`() {
        // Arrange
        val repository = programWithSubmodule()
        val revision = repository.head
        repository.write("README.md", "# hello\n")
        repository.commit()

        // Act
        val outcome = sandbox.kotlinstall("install", repository.url, "--rev", revision, environment = localSubmodules)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should reinstall itself with --force`() {
        // Arrange
        val source = sandbox.source()
        sandbox.install(source.url)

        // Act
        val outcome = sandbox.command("kotlinstall", "install", "--force", source.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(
            Outcome(0, listed("kotlinstall", source.head, source.url, "kotlinstall"), ""),
            sandbox.command("kotlinstall", "list"),
        )
    }

    @Test
    fun `should print its help with --help`() {
        // Act
        val outcome = sandbox.kotlinstall("install", "--help")

        // Assert
        assertEquals(
            Outcome(
                0,
                """
                Usage: kotlinstall install [<options>] <url>

                  Build a Kotlin/Native Gradle project from a Git repository and install its commands

                Options:
                  -h, --help  Show this message and exit

                Arguments:
                  <url>  URL or path of the Git repository
                """.trimIndent(),
                "",
            ),
            outcome,
        )
    }

    private fun programWithSubmodule(): Repository {
        val sources = sandbox.repository("sources")
        sources.write("nativeMain/kotlin/Main.kt", mainFunction("main", "Hello!"))
        sources.commit()
        val repository = sandbox.repository("hello")
        repository.write("settings.gradle.kts", settingsScript("hello"))
        repository.write("build.gradle.kts", buildScript("target.binaries.executable()"))
        repository.wrapper()
        repository.submodule(sources, "src")
        repository.commit()
        return repository
    }
}
