package com.argol.awsprofile.infrastructure.process

import com.argol.awsprofile.ports.ProcessResult
import com.argol.awsprofile.ports.ProcessRunner
import kotlinx.cinterop.*
import platform.posix.*

class NativeProcessRunner : ProcessRunner {

    override fun run(command: String, arguments: List<String>): ProcessResult {
        val allArgs = listOf(command) + arguments
        val pid = fork()

        if (pid < 0) {
            return ProcessResult(exitCode = 1, stdout = "", stderr = "Failed to fork process")
        }

        if (pid == 0) {
            // Child: replace process image with the target executable.
            // stdio is inherited from the parent — interactive commands work correctly.
            memScoped {
                val argv = allocArray<CPointerVar<ByteVar>>(allArgs.size + 1)
                allArgs.forEachIndexed { i, arg ->
                    argv[i] = arg.cstr.getPointer(this)
                }
                argv[allArgs.size] = null
                execvp(command, argv)
            }
            // execvp only returns on failure
            exit(127)
        }

        // Parent: wait for child
        val exitCode = memScoped {
            val status = alloc<IntVar>()
            waitpid(pid, status.ptr, 0)
            (status.value ushr 8) and 0xFF
        }

        return ProcessResult(exitCode = exitCode, stdout = "", stderr = "")
    }

    override fun isAvailable(command: String): Boolean {
        val pathEnv = getenv("PATH")?.toKString() ?: return false
        for (dir in pathEnv.split(":")) {
            if (dir.isBlank()) continue
            val fullPath = "$dir/$command"
            if (access(fullPath, X_OK) == 0) return true
        }
        return false
    }
}
