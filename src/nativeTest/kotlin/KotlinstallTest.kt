package dev.yuyuyuyuyu.kotlinstall

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class KotlinstallTest {
    private val sandbox = Sandbox()
    private val commands = Regex("Commands:\n +install +\\S.*\n +update +\\S.*\n +uninstall +\\S.*\n +list +\\S.*")

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should describe every command with --help`() {
        // Act
        val outcome = sandbox.kotlinstall("--help")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, commands)
    }

    @Test
    fun `should describe every command when no command is given`() {
        // Act
        val outcome = sandbox.kotlinstall()

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, commands)
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
