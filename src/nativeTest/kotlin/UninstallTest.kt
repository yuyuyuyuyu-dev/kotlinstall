package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UninstallTest {
    private val sandbox = Sandbox()

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should remove the package and its commands`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.kotlinstall("uninstall", "hello")

        // Assert
        assertEquals(Outcome(0, "Removed ${sandbox.bin}/hello\nUninstalled hello", ""), outcome)
        assertFalse(FileSystem.SYSTEM.exists(sandbox.bin / "hello"))
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should remove every named package`() {
        // Arrange
        val first = sandbox.repository("first")
        first.program("1")
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        second.program("2")
        sandbox.install(second.url)

        // Act
        val outcome = sandbox.kotlinstall("uninstall", "first", "second")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(emptyList(), FileSystem.SYSTEM.list(sandbox.bin))
        assertEquals(Outcome(0, "", ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should remove nothing when a named package is not installed`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.kotlinstall("uninstall", "hello", "missing")

        // Assert
        assertEquals(Outcome(1, "", "Error: missing is not installed"), outcome)
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should require a package name`() {
        // Act
        val outcome = sandbox.kotlinstall("uninstall")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, "Error: missing argument <package>")
    }

    @Test
    fun `should leave a command that no longer belongs to the package`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("Hello!")
        sandbox.install(repository.url)
        FileSystem.SYSTEM.delete(sandbox.bin / "hello")
        FileSystem.SYSTEM.write(sandbox.bin / "hello") { writeUtf8("mine") }

        // Act
        val outcome = sandbox.kotlinstall("uninstall", "hello")

        // Assert
        assertEquals(Outcome(0, "Uninstalled hello", ""), outcome)
        assertEquals("mine", FileSystem.SYSTEM.read(sandbox.bin / "hello") { readUtf8() })
    }

    @Test
    fun `should uninstall itself and leave the other packages`() {
        // Arrange
        val source = sandbox.source()
        sandbox.install(source.url)
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.command("kotlinstall", "uninstall", "kotlinstall")

        // Assert
        assertEquals(Outcome(0, "Removed ${sandbox.bin}/kotlinstall\nUninstalled kotlinstall", ""), outcome)
        assertFalse(FileSystem.SYSTEM.exists(sandbox.bin / "kotlinstall"))
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
        assertEquals(Outcome(0, listed("hello", revision, repository.url, "hello"), ""), sandbox.kotlinstall("list"))
    }

    @Test
    fun `should print its help with --help`() {
        // Act
        val outcome = sandbox.kotlinstall("uninstall", "--help")

        // Assert
        assertEquals(
            Outcome(
                0,
                """
                Usage: kotlinstall uninstall [<options>] <package>...

                  Remove installed packages and their commands

                Options:
                  -h, --help  Show this message and exit

                Arguments:
                  <package>  Name of the package to remove
                """.trimIndent(),
                "",
            ),
            outcome,
        )
    }
}
