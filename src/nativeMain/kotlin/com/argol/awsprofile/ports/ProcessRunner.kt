package com.argol.awsprofile.ports

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

interface ProcessRunner {
    fun run(command: String, arguments: List<String> = emptyList()): ProcessResult
    fun isAvailable(command: String): Boolean
}
