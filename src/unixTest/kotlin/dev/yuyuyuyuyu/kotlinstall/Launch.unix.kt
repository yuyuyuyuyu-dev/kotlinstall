package dev.yuyuyuyuyu.kotlinstall

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.changeDir
import okio.Path
import kotlin.test.fail

actual fun launch(
    command: List<String>,
    directory: Path,
    environment: Map<String, String?>,
    input: String?,
): Outcome {
    val result =
        Process
            .Builder(command.first())
            .args(command.drop(1))
            .changeDir(directory.toString().toFile())
            .environment {
                environment.forEach { (name, value) -> if (value == null) remove(name) else put(name, value) }
            }.stdin(if (input == null) Stdio.Null else Stdio.Pipe)
            .createOutput {
                timeoutMillis = 30 * 60 * 1000
                if (input != null) inputUtf8 { input }
            }
    result.processError?.let {
        fail("${command.joinToString(" ")} did not finish: $it\n${result.stdout}\n${result.stderr}")
    }
    return Outcome(result.processInfo.exitCode, result.stdout, result.stderr)
}
