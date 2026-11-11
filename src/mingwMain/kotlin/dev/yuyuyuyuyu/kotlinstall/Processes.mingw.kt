package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import okio.Buffer
import okio.Path
import platform.posix.fflush
import platform.posix.stdout
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.CreatePipe
import platform.windows.CreateProcessW
import platform.windows.DUPLICATE_SAME_ACCESS
import platform.windows.DWORDVar
import platform.windows.DuplicateHandle
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GetCurrentProcess
import platform.windows.GetExitCodeProcess
import platform.windows.GetStdHandle
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.HANDLE_FLAG_INHERIT
import platform.windows.INFINITE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.PROCESS_INFORMATION
import platform.windows.ReadFile
import platform.windows.SECURITY_ATTRIBUTES
import platform.windows.STARTF_USESTDHANDLES
import platform.windows.STARTUPINFOW
import platform.windows.STD_ERROR_HANDLE
import platform.windows.STD_INPUT_HANDLE
import platform.windows.STD_OUTPUT_HANDLE
import platform.windows.SetHandleInformation
import platform.windows.TRUE
import platform.windows.WaitForSingleObject

private const val BUFFER_SIZE = 8192

actual fun commandLine(command: List<String>) = command.joinToString(" ", transform = ::quote)

@OptIn(ExperimentalForeignApi::class)
actual fun spawn(
    command: List<String>,
    directory: Path?,
    output: StringBuilder?,
): Int =
    memScoped {
        fflush(stdout)
        val pipe = if (output == null) null else pipe()
        try {
            val process =
                start(
                    command,
                    directory,
                    if (output == null) duplicate(GetStdHandle(STD_INPUT_HANDLE)) else nul(),
                    pipe?.second ?: duplicate(GetStdHandle(STD_OUTPUT_HANDLE)),
                    duplicate(GetStdHandle(STD_ERROR_HANDLE)),
                )
            try {
                pipe?.first?.let { output?.append(read(it)) }
                WaitForSingleObject(process, INFINITE)
                val status = alloc<DWORDVar>()
                GetExitCodeProcess(process, status.ptr)
                status.value.toInt()
            } finally {
                CloseHandle(process)
            }
        } finally {
            pipe?.first?.let(::close)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.start(
    command: List<String>,
    directory: Path?,
    input: HANDLE?,
    output: HANDLE?,
    error: HANDLE?,
): HANDLE? {
    val startup =
        alloc<STARTUPINFOW> {
            cb = sizeOf<STARTUPINFOW>().convert()
            dwFlags = STARTF_USESTDHANDLES.convert()
            hStdInput = input
            hStdOutput = output
            hStdError = error
        }
    val process = alloc<PROCESS_INFORMATION>()
    val line = commandLine(command).wcstr.getPointer(this)
    val started =
        CreateProcessW(null, line, null, null, TRUE, 0u, null, directory?.toString(), startup.ptr, process.ptr)
    val failure = if (started == 0) lastError() else null
    listOfNotNull(input, output, error).forEach(::close)
    if (failure != null) fail("Could not run ${command.first()}: $failure")
    CloseHandle(process.hThread)
    return process.hProcess
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.inheritable(): CPointer<SECURITY_ATTRIBUTES> =
    alloc<SECURITY_ATTRIBUTES> {
        nLength = sizeOf<SECURITY_ATTRIBUTES>().convert()
        bInheritHandle = TRUE
    }.ptr

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.pipe(): Pair<HANDLE?, HANDLE?> {
    val reader = alloc<HANDLEVar>()
    val writer = alloc<HANDLEVar>()
    if (CreatePipe(reader.ptr, writer.ptr, inheritable(), 0u) == 0) fail("Could not create a pipe: ${lastError()}")
    SetHandleInformation(reader.value, HANDLE_FLAG_INHERIT.convert(), 0u)
    return reader.value to writer.value
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.nul(): HANDLE? =
    CreateFileW(
        "NUL",
        GENERIC_READ.convert(),
        (FILE_SHARE_READ or FILE_SHARE_WRITE).convert(),
        inheritable(),
        OPEN_EXISTING.convert(),
        0u,
        null,
    )

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.duplicate(handle: HANDLE?): HANDLE? {
    if (handle == null || handle == INVALID_HANDLE_VALUE) return null
    val copy = alloc<HANDLEVar>()
    val process = GetCurrentProcess()
    val duplicated = DuplicateHandle(process, handle, process, copy.ptr, 0u, TRUE, DUPLICATE_SAME_ACCESS.convert())
    return if (duplicated == 0) null else copy.value
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.read(handle: HANDLE): String {
    val buffer = Buffer()
    val chunk = ByteArray(BUFFER_SIZE)
    val count = alloc<DWORDVar>()
    chunk.usePinned { pinned ->
        while (ReadFile(handle, pinned.addressOf(0), chunk.size.convert(), count.ptr, null) != 0 && count.value > 0u) {
            buffer.write(chunk, 0, count.value.toInt())
        }
    }
    return buffer.readUtf8()
}

@OptIn(ExperimentalForeignApi::class)
private fun close(handle: HANDLE) {
    if (handle != INVALID_HANDLE_VALUE) CloseHandle(handle)
}

private fun quote(argument: String) =
    if (argument.isNotEmpty() &&
        argument.all { it.isLetterOrDigit() || it in "%+,-./:=@_\\" }
    ) {
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
