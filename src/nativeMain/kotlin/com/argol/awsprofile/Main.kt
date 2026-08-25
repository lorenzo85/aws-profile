package com.argol.awsprofile

import com.argol.awsprofile.application.LoginService
import com.argol.awsprofile.cli.Cli
import com.argol.awsprofile.cli.CliParser
import com.argol.awsprofile.cli.ConsoleOutput
import com.argol.awsprofile.infrastructure.aws.AwsConfigFileRepository
import com.argol.awsprofile.infrastructure.aws.AwsSsoDiscovery
import com.argol.awsprofile.infrastructure.aws.SsoCacheReader
import com.argol.awsprofile.infrastructure.config.TomlConfigurationRepository
import com.argol.awsprofile.infrastructure.filesystem.NativeFileSystem
import com.argol.awsprofile.infrastructure.filesystem.NativeUserDirectories
import com.argol.awsprofile.infrastructure.process.NativeProcessRunner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.system.exitProcess

@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    val fileSystem = NativeFileSystem()
    val userDirectories = NativeUserDirectories()
    val processRunner = NativeProcessRunner()

    val configurationRepository = TomlConfigurationRepository(fileSystem, userDirectories)
    val awsConfigRepository = AwsConfigFileRepository(fileSystem, userDirectories)
    val loginService = LoginService(processRunner)
    val ssoDiscovery = AwsSsoDiscovery(processRunner, SsoCacheReader(fileSystem, userDirectories))

    val cli = Cli(
        parser = CliParser(),
        output = ConsoleOutput(),
        configurationRepository = configurationRepository,
        awsConfigRepository = awsConfigRepository,
        loginService = loginService,
        ssoDiscovery = ssoDiscovery
    )

    val exitCode = cli.run(args)
    if (exitCode != 0) exitProcess(exitCode)
}
