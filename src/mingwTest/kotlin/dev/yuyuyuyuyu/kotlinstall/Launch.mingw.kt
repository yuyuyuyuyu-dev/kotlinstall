package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import okio.FileSystem
import okio.Path
import platform.windows.CREATE_ALWAYS
import platform.windows.CREATE_UNICODE_ENVIRONMENT
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.CreateProcessW
import platform.windows.DWORDVar
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.FreeEnvironmentStringsW
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetEnvironmentStringsW
import platform.windows.GetExitCodeProcess
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.PROCESS_INFORMATION
import platform.windows.SECURITY_ATTRIBUTES
import platform.windows.STARTF_USESTDHANDLES
import platform.windows.STARTUPINFOW
import platform.windows.TRUE
import platform.windows.TerminateProcess
import platform.windows.WAIT_OBJECT_0
import platform.windows.WaitForSingleObject
import kotlin.random.Random
import kotlin.test.fail

actual fun launch(
    command: List<String>,
    directory: Path,
    environment: Map<String, String?>,
    input: String?,
): Outcome {
    val work = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kotlinstall-launch-${Random.nextLong().toULong().toString(36)}"
    FileSystem.SYSTEM.createDirectories(work, mustCreate = true)
    try {
        val stdin = work / "stdin"
        val stdout = work / "stdout"
        val stderr = work / "stderr"
        FileSystem.SYSTEM.write(stdin) { writeUtf8(input.orEmpty()) }
        val status = run(command, directory, environment, listOf(stdin, stdout, stderr))
        val outcome = Outcome(status ?: -1, text(stdout), text(stderr))
        if (status == null) fail("${command.joinToString(" ")} did not finish: $outcome")
        return outcome
    } finally {
        FileSystem.SYSTEM.deleteRecursively(work)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun run(
    command: List<String>,
    directory: Path,
    environment: Map<String, String?>,
    streams: List<Path>,
): Int? =
    memScoped {
        val inheritable =
            alloc<SECURITY_ATTRIBUTES> {
                nLength = sizeOf<SECURITY_ATTRIBUTES>().convert()
                bInheritHandle = TRUE
            }
        val handles =
            listOf(
                open(streams[0], GENERIC_READ.convert(), OPEN_EXISTING.convert(), inheritable.ptr),
                open(streams[1], GENERIC_WRITE.convert(), CREATE_ALWAYS.convert(), inheritable.ptr),
                open(streams[2], GENERIC_WRITE.convert(), CREATE_ALWAYS.convert(), inheritable.ptr),
            )
        val startup =
            alloc<STARTUPINFOW> {
                cb = sizeOf<STARTUPINFOW>().convert()
                dwFlags = STARTF_USESTDHANDLES.convert()
                hStdInput = handles[0]
                hStdOutput = handles[1]
                hStdError = handles[2]
            }
        val process = alloc<PROCESS_INFORMATION>()
        val started =
            CreateProcessW(
                null,
                command.joinToString(" ", transform = ::quote).wcstr.getPointer(this),
                null,
                null,
                TRUE,
                CREATE_UNICODE_ENVIRONMENT.convert(),
                environmentBlock(environment).wcstr.getPointer(this),
                directory.toString(),
                startup.ptr,
                process.ptr,
            )
        val error = GetLastError()
        handles.forEach { CloseHandle(it) }
        if (started == 0) fail("Could not run ${command.joinToString(" ")}: error $error")
        CloseHandle(process.hThread)
        try {
            wait(process.hProcess)
        } finally {
            CloseHandle(process.hProcess)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun wait(process: HANDLE?): Int? =
    memScoped {
        if (WaitForSingleObject(process, TIMEOUT_MILLIS.convert()) != WAIT_OBJECT_0.convert<UInt>()) {
            TerminateProcess(process, 1u)
            return null
        }
        val code = alloc<DWORDVar>()
        GetExitCodeProcess(process, code.ptr)
        code.value.toInt()
    }

private const val TIMEOUT_MILLIS = 30 * 60 * 1000

@OptIn(ExperimentalForeignApi::class)
private fun open(
    file: Path,
    access: UInt,
    disposition: UInt,
    attributes: CPointer<SECURITY_ATTRIBUTES>,
): HANDLE? {
    val handle =
        CreateFileW(
            file.toString(),
            access,
            (FILE_SHARE_READ or FILE_SHARE_WRITE).convert(),
            attributes,
            disposition,
            FILE_ATTRIBUTE_NORMAL.convert(),
            null,
        )
    if (handle == INVALID_HANDLE_VALUE) fail("Could not open $file: error ${GetLastError()}")
    return handle
}

private fun text(file: Path) =
    FileSystem.SYSTEM
        .read(file) { readUtf8() }
        .replace("\r\n", "\n")
        .removeSuffix("\n")

@OptIn(ExperimentalForeignApi::class)
private fun environmentBlock(changes: Map<String, String?>): String {
    val block = StringBuilder()
    val strings = GetEnvironmentStringsW() ?: fail("Could not read the environment: error ${GetLastError()}")
    try {
        var index = 0
        while (strings[index] != 0.toUShort() || strings[index + 1] != 0.toUShort()) {
            block.append(strings[index].toInt().toChar())
            index++
        }
    } finally {
        FreeEnvironmentStringsW(strings)
    }
    val variables =
        block
            .split('\u0000')
            .filter { it.indexOf('=', startIndex = 1) > 0 }
            .associate { entry ->
                val separator = entry.indexOf('=', startIndex = 1)
                entry.take(separator).uppercase() to (entry.take(separator) to entry.drop(separator + 1))
            }.toMutableMap()
    changes.forEach { (name, value) ->
        if (value == null) variables.remove(name.uppercase()) else variables[name.uppercase()] = name to value
    }
    return variables.entries
        .sortedBy { it.key }
        .joinToString("") { (_, variable) -> "${variable.first}=${variable.second}\u0000" }
}

private fun quote(argument: String) =
    if (argument.isNotEmpty() && argument.none { it in " \t\"" }) {
        argument
    } else {
        buildString {
            append('"')
            var backslashes = 0
            argument.forEach { character ->
                when (character) {
                    '\\' -> {
                        backslashes++
                    }

                    '"' -> {
                        append("\\".repeat(backslashes * 2 + 1))
                        append(character)
                        backslashes = 0
                    }

                    else -> {
                        append("\\".repeat(backslashes))
                        append(character)
                        backslashes = 0
                    }
                }
            }
            append("\\".repeat(backslashes * 2))
            append('"')
        }
    }
