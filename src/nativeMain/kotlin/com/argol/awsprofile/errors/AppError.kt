package com.argol.awsprofile.errors

sealed class AppError(override val message: String) : Exception(message)

class ConfigurationError(message: String) : AppError(message)

class AccountNotFoundError(alias: String) : AppError("Unknown AWS account: $alias")

class AwsConfigError(message: String) : AppError(message)

class ProcessExecutionError(message: String) : AppError(message)

class ValidationError(message: String) : AppError(message)
