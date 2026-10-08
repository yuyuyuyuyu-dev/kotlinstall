package dev.yuyuyuyuyu.kotlinstall

import okio.Path

data class Reference(val kind: Kind, val value: String) {
    enum class Kind(val option: String) {
        BRANCH("branch"),
        TAG("tag"),
        REV("rev"),
    }

    override fun toString() = "--${kind.option} $value"
}

object Git {
    fun clone(repository: String, reference: Reference?, directory: Path): String {
        val destination = directory.toString()
        when (reference?.kind) {
            null -> execute(
                listOf("git", "clone", "--depth", "1", "--recurse-submodules", "--shallow-submodules", repository, destination),
            )
            Reference.Kind.BRANCH, Reference.Kind.TAG -> execute(
                listOf(
                    "git", "clone", "--depth", "1", "--branch", reference.value,
                    "--recurse-submodules", "--shallow-submodules", repository, destination,
                ),
            )
            Reference.Kind.REV -> {
                execute(listOf("git", "clone", "--no-checkout", repository, destination))
                execute(listOf("git", "checkout", "--detach", reference.value), directory)
                execute(listOf("git", "submodule", "update", "--init", "--recursive"), directory)
            }
        }
        return capture(listOf("git", "rev-parse", "HEAD"), directory).trim()
    }
}
