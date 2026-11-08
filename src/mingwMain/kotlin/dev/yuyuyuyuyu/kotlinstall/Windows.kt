package dev.yuyuyuyuyu.kotlinstall

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import platform.windows.FORMAT_MESSAGE_ALLOCATE_BUFFER
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageW
import platform.windows.GetLastError
import platform.windows.LPWSTRVar
import platform.windows.LocalFree

@OptIn(ExperimentalForeignApi::class)
fun lastError(): String {
    val code = GetLastError()
    return memScoped {
        val message = alloc<LPWSTRVar>()
        val length =
            FormatMessageW(
                (FORMAT_MESSAGE_ALLOCATE_BUFFER or FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS)
                    .convert(),
                null,
                code,
                0u,
                message.ptr.reinterpret(),
                0u,
                null,
            )
        val text =
            message.value
                ?.takeIf { length > 0u }
                ?.toKStringFromUtf16()
                ?.trim()
        LocalFree(message.value)
        text ?: "error $code"
    }
}
