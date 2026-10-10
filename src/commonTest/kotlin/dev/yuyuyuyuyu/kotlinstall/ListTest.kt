package dev.yuyuyuyuyu.kotlinstall

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ListTest {
    private val sandbox = Sandbox()

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should print nothing when no package is installed`() {
        // Act
        val outcome = sandbox.kotlinstall("list")

        // Assert
        assertEquals(Outcome(0, "", ""), outcome)
    }

    @Test
    fun `should list each package with its commands and source and revision`() {
        // Arrange
        val first = sandbox.repository("first")
        val firstRevision = first.programs(mapOf("one" to "1", "two" to "2"))
        val second = sandbox.repository("second")
        second.program("main")
        second.branch("next")
        val secondRevision = second.program("next")
        sandbox.kotlinstall("install", second.url, "--branch", "next")
        sandbox.kotlinstall("install", first.url)

        // Act
        val outcome = sandbox.kotlinstall("list")

        // Assert
        assertEquals(
            Outcome(
                0,
                """
                package:  first
                commands: one, two
                source:   ${first.url}
                revision: ${firstRevision.take(7)}

                package:  second
                commands: second
                source:   ${second.url} --branch next
                revision: ${secondRevision.take(7)}
                """.trimIndent(),
                "",
            ),
            outcome,
        )
    }

    @Test
    fun `should print its help with --help`() {
        // Act
        val outcome = sandbox.kotlinstall("list", "--help")

        // Assert
        assertEquals(
            Outcome(
                0,
                """
                Usage: kotlinstall list [<options>]

                  List the installed packages and their commands

                Options:
                  -h, --help  Show this message and exit
                """.trimIndent(),
                "",
            ),
            outcome,
        )
    }
}
