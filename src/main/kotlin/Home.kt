package dev.yuyuyuyuyu.kotlinstall

import java.io.StringWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.reader
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

data class Receipt(
    val name: String,
    val source: String,
    val reference: Reference?,
    val platform: Platform?,
    val revision: String,
    val commands: List<String>,
) {
    fun isFrom(repository: String) = source.removeSuffix(".git") == repository.removeSuffix(".git")

    override fun toString() =
        "$name ${revision.take(7)} (" + listOfNotNull(source, reference, platform?.let { "--platform ${it.option}" }).joinToString(" ") + ")"

    fun write(directory: Path) {
        val properties = Properties()
        properties["source"] = source
        reference?.let { properties[it.kind.option] = it.value }
        platform?.let { properties["platform"] = it.option }
        properties["revision"] = revision
        properties["commands"] = commands.joinToString(",")
        val text = StringWriter().also { properties.store(it, null) }.toString()
        directory.resolve(FILE).writeText(text.lineSequence().filterNot { it.startsWith("#") }.joinToString("\n"))
    }

    companion object {
        private const val FILE = "kotlinstall.properties"

        fun read(directory: Path): Receipt? {
            val file = directory.resolve(FILE)
            if (!file.isRegularFile()) return null
            val properties = Properties().apply { file.reader().use { load(it) } }
            return Receipt(
                name = directory.name,
                source = properties.getProperty("source") ?: return null,
                reference = Reference.Kind.entries.firstNotNullOfOrNull { kind ->
                    properties.getProperty(kind.option)?.let { Reference(kind, it) }
                },
                platform = Platform.entries.firstOrNull { it.option == properties.getProperty("platform") },
                revision = properties.getProperty("revision") ?: return null,
                commands = properties.getProperty("commands")?.split(',')?.filter { it.isNotEmpty() } ?: return null,
            )
        }
    }
}

@OptIn(ExperimentalPathApi::class)
class Home(root: Path) {
    val root: Path = root.toAbsolutePath().normalize()
    val bin: Path = this.root.resolve("bin")
    val packages: Path = this.root.resolve("packages")

    fun receipt(name: String) = Receipt.read(packages.resolve(name))

    fun receipts(): List<Receipt> =
        if (packages.isDirectory()) {
            packages.listDirectoryEntries().filterNot { it.name.startsWith(".") }.mapNotNull { Receipt.read(it) }.sortedBy { it.name }
        } else {
            emptyList()
        }

    fun owner(command: String): String? {
        val link = bin.resolve(command)
        if (!link.isSymbolicLink()) return null
        val target = bin.resolve(link.readSymbolicLink()).normalize()
        return if (target.startsWith(packages) && target != packages) packages.relativize(target).first().name else null
    }

    fun conflicts(name: String, commands: Collection<String>) =
        commands.filter { bin.resolve(it).exists(LinkOption.NOFOLLOW_LINKS) && owner(it) != name }

    fun <T> lock(action: () -> T): T {
        root.createDirectories()
        return FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }

    fun stage(name: String): Path =
        createTempDirectory(packages.createDirectories(), ".$name-").also { it.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x")) }

    fun install(receipt: Receipt, staging: Path, targets: Map<String, Path>) {
        receipt.write(staging)
        bin.createDirectories()
        targets.keys.forEach { release(it, receipt.name) }
        receipt(receipt.name)?.commands?.forEach { unlink(it, receipt.name) }
        val directory = packages.resolve(receipt.name)
        val previous = if (directory.exists()) stage(receipt.name).also { directory.moveTo(it.resolve(receipt.name)) } else null
        staging.moveTo(directory, StandardCopyOption.ATOMIC_MOVE)
        targets.forEach { (command, target) ->
            Files.createSymbolicLink(bin.resolve(command), bin.relativize(directory.resolve(target)))
        }
        previous?.deleteRecursively()
    }

    fun uninstall(receipt: Receipt): List<String> {
        val removed = receipt.commands.filter { unlink(it, receipt.name) }
        packages.resolve(receipt.name).deleteRecursively()
        return removed
    }

    private fun unlink(command: String, name: String) = owner(command) == name && bin.resolve(command).deleteIfExists()

    private fun release(command: String, name: String) {
        val link = bin.resolve(command)
        if (!link.exists(LinkOption.NOFOLLOW_LINKS)) return
        val owner = owner(command)
        if (owner == name) return
        if (link.isDirectory(LinkOption.NOFOLLOW_LINKS)) fail("$link is a directory")
        link.deleteIfExists()
        val receipt = owner?.let(::receipt) ?: return
        val commands = receipt.commands - command
        if (commands.isEmpty()) {
            packages.resolve(owner).deleteRecursively()
        } else {
            receipt.copy(commands = commands).write(packages.resolve(owner))
        }
    }

    companion object {
        fun current() = Home(
            System.getenv("KOTLINSTALL_HOME")?.takeIf { it.isNotBlank() }?.let(::Path)
                ?: Path(System.getProperty("user.home"), ".kotlinstall"),
        )
    }
}
