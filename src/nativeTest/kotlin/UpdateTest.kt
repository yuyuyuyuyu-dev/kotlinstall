package dev.yuyuyuyuyu.kotlinstall

import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UpdateTest {
    private val sandbox = Sandbox()

    @AfterTest
    fun cleanUp() = sandbox.delete()

    @Test
    fun `should do nothing when no package is installed`() {
        // Act
        val outcome = sandbox.kotlinstall("update")

        // Assert
        assertEquals(Outcome(0, "", ""), outcome)
    }

    @Test
    fun `should reinstall every package whose source has new commits`() {
        // Arrange
        val first = sandbox.repository("first")
        first.program("first 1")
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        second.program("second 1")
        sandbox.install(second.url)
        first.program("first 2")
        second.program("second 2")

        // Act
        val outcome = sandbox.kotlinstall("update")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "first 2", ""), sandbox.command("first"))
        assertEquals(Outcome(0, "second 2", ""), sandbox.command("second"))
    }

    @Test
    fun `should leave an up-to-date package as it is`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.kotlinstall("update")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "hello ${revision.take(7)} (${repository.url}) is up to date. Use --force to reinstall it.")
        assertFalse("Installed hello" in outcome.output, "$outcome")
    }

    @Test
    fun `should update only the named packages`() {
        // Arrange
        val first = sandbox.repository("first")
        first.program("first 1")
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        second.program("second 1")
        sandbox.install(second.url)
        first.program("first 2")
        second.program("second 2")

        // Act
        val outcome = sandbox.kotlinstall("update", "first")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "first 2", ""), sandbox.command("first"))
        assertEquals(Outcome(0, "second 1", ""), sandbox.command("second"))
    }

    @Test
    fun `should follow the branch that the package was installed from`() {
        // Arrange
        val repository = sandbox.repository("hello")
        repository.program("main 1")
        repository.branch("next")
        repository.program("next 1")
        sandbox.install(repository.url, "--branch", "next")
        val revision = repository.program("next 2")
        repository.checkout("main")
        repository.program("main 2")

        // Act
        val outcome = sandbox.kotlinstall("update")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(Outcome(0, "next 2", ""), sandbox.command("hello"))
        assertEquals(
            Outcome(0, "hello ${revision.take(7)} (${repository.url} --branch next)\n    hello", ""),
            sandbox.kotlinstall("list"),
        )
    }

    @Test
    fun `should keep a package at the revision that it was installed from`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("first")
        sandbox.install(repository.url, "--rev", revision)
        repository.program("second")

        // Act
        val outcome = sandbox.kotlinstall("update")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "hello ${revision.take(7)} (${repository.url} --rev $revision) is up to date.")
        assertEquals(Outcome(0, "first", ""), sandbox.command("hello"))
    }

    @Test
    fun `should reinstall an up-to-date package with --force`() {
        // Arrange
        val repository = sandbox.repository("hello")
        val revision = repository.program("Hello!")
        sandbox.install(repository.url)

        // Act
        val outcome = sandbox.kotlinstall("update", "--force")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertContains(outcome.output, "Installed hello ${revision.take(7)} (${repository.url})")
        assertEquals(Outcome(0, "Hello!", ""), sandbox.command("hello"))
    }

    @Test
    fun `should fail for a package that is not installed`() {
        // Act
        val outcome = sandbox.kotlinstall("update", "missing")

        // Assert
        assertEquals(Outcome(1, "", "Error: missing is not installed"), outcome)
    }

    @Test
    fun `should update the other packages when one cannot be updated`() {
        // Arrange
        val first = sandbox.repository("first")
        first.program("first 1")
        sandbox.install(first.url)
        val second = sandbox.repository("second")
        second.program("second 1")
        sandbox.install(second.url)
        FileSystem.SYSTEM.deleteRecursively(first.directory)
        second.program("second 2")

        // Act
        val outcome = sandbox.kotlinstall("update")

        // Assert
        assertEquals(1, outcome.status, "$outcome")
        assertContains(outcome.error, Regex("Error: git clone .* failed with exit code 128"))
        assertContains(outcome.error, "Error: Could not update first")
        assertEquals(Outcome(0, "first 1", ""), sandbox.command("first"))
        assertEquals(Outcome(0, "second 2", ""), sandbox.command("second"))
    }

    @Test
    fun `should update itself`() {
        // Arrange
        val source = sandbox.source()
        sandbox.install(source.url)
        source.write("NOTE", "A new commit\n")
        val revision = source.commit()

        // Act
        val outcome = sandbox.command("kotlinstall", "update")

        // Assert
        assertEquals(0, outcome.status, "$outcome")
        assertEquals(
            Outcome(0, "kotlinstall ${revision.take(7)} (${source.url})\n    kotlinstall", ""),
            sandbox.command("kotlinstall", "list"),
        )
    }
}
