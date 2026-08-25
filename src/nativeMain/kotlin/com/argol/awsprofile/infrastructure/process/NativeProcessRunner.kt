package com.argol.awsprofile.infrastructure.process

import com.argol.awsprofile.ports.ProcessResult
import com.argol.awsprofile.ports.ProcessRunner
import kotlinx.cinterop.*
import platform.posix.*

@ExperimentalForeignApi
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

    @OptIn(ExperimentalForeignApi::class)
    override fun capture(command: String, arguments: List<String>): ProcessResult {
        val allArgs = listOf(command) + arguments
        val cmd = allArgs.joinToString(" ") { escapeShellArg(it) } + " 2>&1"
        val pipe = popen(cmd, "r")
            ?: return ProcessResult(exitCode = 1, stdout = "", stderr = "Failed to run: $command")
        val output = StringBuilder()
        memScoped {
            val buffer = allocArray<ByteVar>(8192)
            while (fgets(buffer, 8192, pipe) != null) {
                output.append(buffer.toKString())
            }
        }
        val rawStatus = pclose(pipe)
        val exitCode = if (rawStatus < 0) 1 else (rawStatus ushr 8) and 0xFF
        val out = output.toString().trim()
        return ProcessResult(
            exitCode = exitCode,
            stdout = if (exitCode == 0) out else "",
            stderr = if (exitCode != 0) out else ""
        )
    }

    private fun escapeShellArg(arg: String): String = "'${arg.replace("'", "'\\''")}'"

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
