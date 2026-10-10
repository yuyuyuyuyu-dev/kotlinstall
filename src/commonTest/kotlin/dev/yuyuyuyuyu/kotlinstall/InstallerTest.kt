package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class InstallerTest {
    private val sandbox = Sandbox()
    private val usage = "Usage: install-kotlinstall.main.kts [<options>]"
    private val help =
        """
        Usage: install-kotlinstall.main.kts [<options>]

        Options:
          -h, --help  Show this message and exit
        """.trimIndent()

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should install kotlinstall`() {
        // Arrange
        val source = sandbox.source()

        // Act
        val outcome = sandbox.installer("--git", source.url)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(
            Outcome(0, listed("kotlinstall", source.head, source.url, "kotlinstall"), ""),
            sandbox.command("kotlinstall", "list"),
        )
    }

    @Test
    fun `should install the given branch`() {
        // Arrange
        val source = sandbox.source()
        source.branch("next")
        source.write("NOTE", "A commit on the branch\n")
        val revision = source.commit()
        source.checkout("main")

        // Act
        val outcome = sandbox.installer("--git", source.url, "--branch", "next")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(
            Outcome(0, listed("kotlinstall", revision, "${source.url} --branch next", "kotlinstall"), ""),
            sandbox.command("kotlinstall", "list"),
        )
    }

    @Test
    fun `should install the given revision`() {
        // Arrange
        val source = sandbox.source()
        val revision = source.head
        source.write("NOTE", "A later commit\n")
        source.commit()

        // Act
        val outcome = sandbox.installer("--git", source.url, "--rev", revision)

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(
            Outcome(0, listed("kotlinstall", revision, "${source.url} --rev $revision", "kotlinstall"), ""),
            sandbox.command("kotlinstall", "list"),
        )
    }

    @Test
    fun `should replace a file that kotlinstall did not install with --force`() {
        // Arrange
        val source = sandbox.source()
        FileSystem.SYSTEM.createDirectories(sandbox.bin)
        FileSystem.SYSTEM.write(sandbox.bin / "kotlinstall") { writeUtf8("mine") }

        // Act
        val outcome = sandbox.installer("--git", source.url, "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(
            Outcome(0, listed("kotlinstall", source.head, source.url, "kotlinstall"), ""),
            sandbox.command("kotlinstall", "list"),
        )
    }

    @Test
    fun `should warn when the bin directory is not in PATH`() {
        // Arrange
        val source = sandbox.source()

        // Act
        val outcome = sandbox.installer("--git", source.url)

        // Assert
        assertContains(
            outcome.error,
            "Warning: ${sandbox.bin} is not in PATH. Add it to run the installed commands, for example: " +
                "export PATH=\"${sandbox.bin}:\$PATH\"",
        )
    }

    @Test
    fun `should not warn when the bin directory is in PATH`() {
        // Arrange
        val source = sandbox.source()
        val path = "${sandbox.bin}:${setting("PATH")}"

        // Act
        val outcome = sandbox.installer("--git", source.url, environment = mapOf("PATH" to path))

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertFalse("is not in PATH" in outcome.error, "$outcome")
    }

    @Test
    fun `should print its help with --help`() {
        // Act
        val outcome = sandbox.installer("--help")

        // Assert
        assertEquals(Outcome(0, help, ""), outcome)
    }

    @Test
    fun `should print its help with -h`() {
        // Act
        val outcome = sandbox.installer("-h")

        // Assert
        assertEquals(Outcome(0, help, ""), outcome)
    }

    @Test
    fun `should fail for an unknown argument`() {
        // Act
        val outcome = sandbox.installer("--unknown")

        // Assert
        assertEquals(Outcome(1, "", "Error: Unknown argument --unknown\n$usage"), outcome)
    }

    @Test
    fun `should fail for an option without its value`() {
        // Act
        val outcome = sandbox.installer("--git")

        // Assert
        assertEquals(Outcome(1, "", "Error: --git needs a value\n$usage"), outcome)
    }

    @Test
    fun `should refuse more than one of branch and tag and revision`() {
        // Act
        val outcome = sandbox.installer("--branch", "next", "--rev", "0000000")

        // Assert
        assertEquals(Outcome(1, "", "Error: Only one of --branch, --tag and --rev can be given\n$usage"), outcome)
    }

    @Test
    fun `should fail when the repository cannot be cloned`() {
        // Act
        val outcome = sandbox.installer("--git", "file://${sandbox.root}/missing")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, Regex("Error: git clone .* failed with exit code 128"))
    }

    @Test
    fun `should remove its work directory when the installation fails`() {
        // Act
        val outcome = sandbox.installer("--git", "file://${sandbox.root}/missing")

        // Assert
        val work = Regex("Removed (.+)").find(outcome.output)?.groupValues?.get(1)
        assertNotNull(work, "$outcome")
        assertFalse(FileSystem.SYSTEM.exists(work.toPath()))
    }
}
