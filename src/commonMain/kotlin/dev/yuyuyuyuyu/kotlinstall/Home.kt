package dev.yuyuyuyuyu.kotlinstall

import okio.Path
import okio.Path.Companion.toPath

data class Receipt(
    val name: String,
    val source: String,
    val reference: Reference?,
    val revision: String,
    val commands: List<String>,
) {
    fun isFrom(repository: String) = source.removeSuffix(".git") == repository.removeSuffix(".git")

    val shortRevision = revision.take(SHORT_REVISION_LENGTH)

    override fun toString() = "$name $shortRevision (" + listOfNotNull(source, reference).joinToString(" ") + ")"

    fun write(directory: Path) {
        val properties =
            listOfNotNull(
                "source" to source,
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
        val link = files.metadataOrNull(bin / command)?.symlinkTarget ?: return null
        val target = bin.resolve(link, normalize = true).segments
        val prefix = packages.segments
        return if (target.size > prefix.size && target.take(prefix.size) == prefix) target[prefix.size] else null
    }

    fun conflicts(
        name: String,
        commands: Collection<String>,
    ) = commands.filter { files.exists(bin / it) && owner(it) != name }

    fun <T> lock(action: () -> T): T {
        files.createDirectories(root)
        return withLock(root / ".lock", action)
    }

    fun stage(name: String): Path {
        files.createDirectories(packages)
        return createTemporaryDirectory(packages, ".$name-").also { setMode(it, "755") }
    }

    fun install(
        receipt: Receipt,
        staging: Path,
        targets: Map<String, Path>,
    ) {
        receipt.write(staging)
        files.createDirectories(bin)
        targets.keys.forEach { release(it, receipt.name) }
        receipt(receipt.name)?.commands?.forEach { unlink(it, receipt.name) }
        val directory = packages / receipt.name
        val previous =
            if (files.exists(directory)) {
                stage(receipt.name).also { files.atomicMove(directory, it / receipt.name) }
            } else {
                null
            }
        files.atomicMove(staging, directory)
        targets.forEach { (command, target) ->
            files.createSymlink(bin / command, (directory / target).relativeTo(bin))
        }
        previous?.let { files.deleteRecursively(it) }
    }

    fun uninstall(receipt: Receipt): List<String> {
        val removed = receipt.commands.filter { unlink(it, receipt.name) }
        files.deleteRecursively(packages / receipt.name)
        return removed
    }

    private fun unlink(
        command: String,
        name: String,
    ): Boolean {
        if (owner(command) != name) return false
        files.delete(bin / command)
        return true
    }

    private fun release(
        command: String,
        name: String,
    ) {
        val link = bin / command
        val metadata = files.metadataOrNull(link)
        val owner = owner(command)
        if (metadata == null || owner == name) return
        if (metadata.isDirectory) fail("Cannot replace the directory $link")
        files.delete(link)
        val receipt = owner?.let(::receipt) ?: return
        val commands = receipt.commands - command
        if (commands.isEmpty()) {
            files.deleteRecursively(packages / owner)
        } else {
            receipt.copy(commands = commands).write(packages / owner)
        }
    }

    companion object {
        fun current() = Home(environment("KOTLINSTALL_HOME")?.toPath() ?: (userHome() / ".kotlinstall"))
    }
}
