package dev.yuyuyuyuyu.kotlinstall

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class KotlinstallTest {
    private val sandbox = Sandbox()
    private val help = """
        Usage: kotlinstall [<options>] <command> [<args>]...

          Install Kotlin/Native programs from Git repositories

        Options:
          -h, --help  Show this message and exit

        Commands:
          install    Build a Kotlin/Native Gradle project from a Git repository and install its commands
          uninstall  Remove installed packages and their commands
    """.trimIndent()

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should print its help with --help`() {
        // Act
        val outcome = sandbox.kotlinstall("--help")

        // Assert
        assertEquals(Outcome(0, help, ""), outcome)
    }

    @Test
    fun `should print its help when no command is given`() {
        // Act
        val outcome = sandbox.kotlinstall()

        // Assert
        assertEquals(Outcome(0, help, ""), outcome)
    }

    @Test
    fun `should fail for an unknown command`() {
        // Act
        val outcome = sandbox.kotlinstall("upgrade")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, "Error: no such subcommand upgrade")
    }
}
