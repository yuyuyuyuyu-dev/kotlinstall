import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.system.exitProcess

val usage = "Usage: install-kotlinstall.main.kts [--git URL] [--branch BRANCH | --tag TAG | --rev REV] [--force]"

class Failure(message: String) : Exception(message)

fun run(directory: File?, vararg command: String) {
    val status = try {
        ProcessBuilder(*command).directory(directory).inheritIO().start().waitFor()
    } catch (e: IOException) {
        throw Failure("Could not run ${command.first()}: ${e.message}")
    }
    if (status != 0) throw Failure("${command.joinToString(" ")} failed with exit code $status")
}

fun announce(phase: String) {
    val title = "==> $phase"
    val rule = "=".repeat(title.length + 1)
    println("\n$rule\n$title\n$rule")
}

fun value(arguments: Iterator<String>, option: String) =
    if (arguments.hasNext()) arguments.next() else throw Failure("$option needs a value\n$usage")

fun target(): String {
    val os = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    return when {
        os.startsWith("Mac") && arch in setOf("aarch64", "arm64") -> "macosArm64"
        os.startsWith("Mac") && arch in setOf("x86_64", "amd64") -> "macosX64"
        os.startsWith("Linux") && arch in setOf("x86_64", "amd64") -> "linuxX64"
        else -> throw Failure("Kotlin/Native cannot build programs on this host")
    }
}

fun install(arguments: Iterator<String>) {
    var repository = "https://github.com/yuyuyuyuyu-dev/kotlinstall.git"
    val reference = mutableListOf<String>()
    val options = mutableListOf<String>()
    while (arguments.hasNext()) {
        when (val argument = arguments.next()) {
            "--git" -> repository = value(arguments, argument)
            "--branch", "--tag", "--rev" -> {
                if (reference.isNotEmpty()) throw Failure("Only one of --branch, --tag and --rev can be given\n$usage")
                reference += listOf(argument, value(arguments, argument))
            }
            "--force" -> options += argument
            "-h", "--help" -> return println(usage)
            else -> throw Failure("Unknown argument $argument\n$usage")
        }
    }
    val target = target()
    val work = Files.createTempDirectory("kotlinstall-").toFile()
    try {
        val source = File(work, "kotlinstall")
        announce("Preparing a temporary kotlinstall")
        when (reference.firstOrNull()) {
            null -> run(null, "git", "clone", "--depth", "1", repository, source.path)
            "--rev" -> {
                run(null, "git", "clone", "--no-checkout", repository, source.path)
                run(source, "git", "checkout", "--detach", reference.last())
            }
            else -> run(null, "git", "clone", "--depth", "1", "--branch", reference.last(), repository, source.path)
        }
        run(source, "sh", "gradlew", "--no-daemon", "linkReleaseExecutable${target.replaceFirstChar(Char::uppercaseChar)}")
        announce("Installing kotlinstall using the temporary kotlinstall")
        run(null, File(source, "build/bin/$target/releaseExecutable/kotlinstall.kexe").path, "install", repository, *reference.toTypedArray(), *options.toTypedArray())
    } finally {
        announce("Cleaning up the work directory")
        if (work.deleteRecursively()) println("Removed $work") else System.err.println("\nWarning: Could not remove $work")
    }
}

try {
    install(args.iterator())
} catch (failure: Failure) {
    System.err.println("Error: ${failure.message}")
    exitProcess(1)
}
