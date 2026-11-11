package dev.yuyuyuyuyu.kotlinstall

import okio.Path
import okio.Path.Companion.toPath

data class Receipt(
    val name: String,
    val source: String,
    val subdirectory: String?,
    val reference: Reference?,
    val revision: String,
    val commands: List<String>,
) {
    fun isFrom(
        repository: String,
        subdirectory: String?,
    ) = source.removeSuffix(".git") == repository.removeSuffix(".git") && this.subdirectory == subdirectory

    val shortRevision = revision.take(SHORT_REVISION_LENGTH)

    val origin = listOfNotNull(source, subdirectory?.let { "--subdirectory=$it" }, reference).joinToString(" ")

    override fun toString() = "$name $shortRevision ($origin)"

    fun write(directory: Path) {
        val properties =
            listOfNotNull(
                "source" to source,
                subdirectory?.let { "subdirectory" to it },
                reference?.let { it.kind.option to it.value },
                "revision" to revision,
                "commands" to commands.joinToString(","),
            )
        files.write(directory / FILE) { writeUtf8(properties.joinToString("") { (key, value) -> "$key=$value\n" }) }
    }

    companion object {
        private const val FILE = "kotlinstall.properties"
        private const val SHORT_REVISION_LENGTH = 7

        fun read(directory: Path): Receipt? {
            val properties = properties(directory / FILE)
            val source = properties["source"]
            val revision = properties["revision"]
            val commands = properties["commands"]
            return if (source == null || revision == null || commands == null) {
                null
            } else {
                Receipt(
                    name = directory.name,
                    source = source,
                    subdirectory = properties["subdirectory"],
                    reference =
                        Reference.Kind.entries.firstNotNullOfOrNull { kind ->
                            properties[kind.option]?.let { Reference(kind, it) }
                        },
                    revision = revision,
                    commands = commands.split(',').filter { it.isNotEmpty() },
                )
            }
        }

        private fun properties(file: Path): Map<String, String> =
            if (isRegularFile(file)) {
                files
                    .read(file) { readUtf8() }
                    .lines()
                    .filter { '=' in it }
                    .associate { it.substringBefore('=') to it.substringAfter('=') }
            } else {
                emptyMap()
            }
    }
}

class Home(
    root: Path,
) {
    val root: Path = absolute(root)
    val bin: Path = this.root / "bin"
    val packages: Path = this.root / "packages"
    private val trash = Trash(this.root / ".trash")

    fun command(name: String) = bin / "$name$executableSuffix"

    fun receipt(name: String) = Receipt.read(packages / name)

    fun receipts(): List<Receipt> =
        if (isDirectory(packages)) {
            files
                .list(packages)
                .filterNot { it.name.startsWith(".") }
                .mapNotNull { Receipt.read(it) }
                .sortedBy { it.name }
        } else {
            emptyList()
        }

    fun owner(command: String): String? {
        val file = command(command)
        if (files.metadataOrNull(file) == null) return null
        return receipts().map { it.name }.firstOrNull { isCommandOf(file, executable(packages / it, command)) }
    }

    fun conflicts(
        name: String,
        commands: Collection<String>,
    ) = commands.filter { files.exists(command(it)) && owner(it) != name }

    fun <T> lock(action: () -> T): T {
        files.createDirectories(root)
        return withLock(root / ".lock") {
            trash.empty()
            action()
        }
    }

    fun stage(name: String): Path {
        files.createDirectories(packages)
        return createTemporaryDirectory(packages, ".$name-").also { setMode(it, "755") }
    }

    fun install(
        receipt: Receipt,
        staging: Path,
    ) {
        receipt.write(staging)
        files.createDirectories(bin)
        receipt.commands.forEach { release(it, receipt.name) }
        receipt(receipt.name)?.commands?.forEach { unlink(it, receipt.name) }
        val directory = packages / receipt.name
        val previous =
            if (files.exists(directory)) {
                stage(receipt.name).also { files.atomicMove(directory, it / receipt.name) }
            } else {
                null
            }
        files.atomicMove(staging, directory)
        receipt.commands.forEach { putCommand(command(it), executable(directory, it)) }
        previous?.let { deleteTree(it) }
    }

    fun uninstall(receipt: Receipt): List<String> {
        val removed = receipt.commands.filter { unlink(it, receipt.name) }
        deleteTree(packages / receipt.name)
        return removed
    }

    private fun unlink(
        command: String,
        name: String,
    ): Boolean {
        if (owner(command) != name) return false
        trash.discard(command(command))
        return true
    }

    private fun release(
        command: String,
        name: String,
    ) {
        val file = command(command)
        val metadata = files.metadataOrNull(file)
        val owner = owner(command)
        if (metadata == null || owner == name) return
        if (metadata.isDirectory) fail("Cannot replace the directory $file")
        trash.discard(file)
        val receipt = owner?.let(::receipt) ?: return
        val commands = receipt.commands - command
        if (commands.isEmpty()) {
            deleteTree(packages / owner)
        } else {
            receipt.copy(commands = commands).write(packages / owner)
        }
    }

    companion object {
        fun current() = Home(environment("KOTLINSTALL_HOME")?.toPath() ?: (userHome() / ".kotlinstall"))
    }
}

fun executable(
    directory: Path,
    command: String,
) = directory / "native" / "$command$executableSuffix"
