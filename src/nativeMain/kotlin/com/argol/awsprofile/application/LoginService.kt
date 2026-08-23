package com.argol.awsprofile.application

import com.argol.awsprofile.errors.ProcessExecutionError
import com.argol.awsprofile.ports.ProcessRunner

class LoginService(
    private val processRunner: ProcessRunner
) {
    fun login(profileName: String): Result<Unit> {
        if (!processRunner.isAvailable("aws")) {
            return Result.failure(
                ProcessExecutionError("AWS CLI executable 'aws' was not found in PATH.")
            )
        }
        val result = processRunner.run("aws", listOf("sso", "login", "--profile", profileName))
        return if (result.exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(
                ProcessExecutionError("aws sso login exited with code ${result.exitCode}")
            )
        }
    }
}
