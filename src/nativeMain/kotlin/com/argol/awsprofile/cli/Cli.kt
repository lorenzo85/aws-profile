package com.argol.awsprofile.cli

import com.argol.awsprofile.APP_VERSION
import com.argol.awsprofile.application.AccountResolver
import com.argol.awsprofile.application.CurrentProfileService
import com.argol.awsprofile.application.LoginService
import com.argol.awsprofile.application.ProfileSwitcher
import com.argol.awsprofile.domain.AccessLevel
import com.argol.awsprofile.domain.AppConfig
import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.errors.AppError
import com.argol.awsprofile.errors.ValidationError
import com.argol.awsprofile.ports.ConfigurationRepository
import com.argol.awsprofile.ports.AwsConfigRepository

object ExitCodes {
    const val SUCCESS = 0
    const val INVALID_ARGUMENTS = 2
    const val CONFIGURATION_ERROR = 3
    const val ACCOUNT_NOT_FOUND = 4
    const val AWS_CONFIG_ERROR = 5
    const val PROCESS_ERROR = 6
}

class Cli(
    private val parser: CliParser,
    private val output: CliOutput,
    private val configurationRepository: ConfigurationRepository,
    private val awsConfigRepository: AwsConfigRepository,
    private val loginService: LoginService
) {
    fun run(args: Array<String>): Int {
        val command = try {
            parser.parse(args)
        } catch (e: ValidationError) {
            output.error("Error: ${e.message}")
            return ExitCodes.INVALID_ARGUMENTS
        }

        return try {
            dispatch(command)
        } catch (e: AppError) {
            output.error("Error: ${e.message}")
            exitCodeFor(e)
        } catch (e: Exception) {
            output.error("Unexpected error: ${e.message}")
            ExitCodes.AWS_CONFIG_ERROR
        }
    }

    private fun dispatch(command: CliCommand): Int = when (command) {
        is SwitchCommand -> handleSwitch(command)
        is ListCommand -> handleList()
        is ListVerboseCommand -> handleListVerbose()
        is CurrentCommand -> handleCurrent(command)
        is LoginCommand -> handleLogin(command)
        is ValidateCommand -> handleValidate(command)
        is VersionCommand -> { output.info("aws-profile $APP_VERSION"); ExitCodes.SUCCESS }
        is HelpCommand -> { printHelp(); ExitCodes.SUCCESS }
    }

    private fun handleSwitch(command: SwitchCommand): Int {
        val switcher = ProfileSwitcher(configurationRepository, awsConfigRepository)
        val profile = switcher.switch(command.selection)
        val level = command.selection.accessLevel
        printProfileConfirmation(profile, level)
        return ExitCodes.SUCCESS
    }

    private fun handleList(): Int {
        val resolver = AccountResolver(configurationRepository)
        resolver.list().forEach { output.info(it.alias) }
        return ExitCodes.SUCCESS
    }

    private fun handleListVerbose(): Int {
        val resolver = AccountResolver(configurationRepository)
        val accounts = resolver.list()
        val maxAlias = accounts.maxOfOrNull { it.alias.length } ?: 0
        accounts.forEach { account ->
            val pad = " ".repeat(maxAlias - account.alias.length)
            output.info("${account.alias}$pad    ${account.accountId}    ${account.region}")
        }
        return ExitCodes.SUCCESS
    }

    private fun handleCurrent(command: CurrentCommand): Int {
        val service = CurrentProfileService(awsConfigRepository)
        val config = configurationRepository.load()
        val profileName = command.profileName

        if (profileName != null) {
            val profile = service.current(profileName)
            if (profile == null) {
                output.error("No configured profile found for: $profileName")
                return ExitCodes.ACCOUNT_NOT_FOUND
            }
            printCurrentProfile(profile, config)
        } else {
            // Show all aws-profile managed profiles from config
            val resolver = AccountResolver(configurationRepository)
            val accounts = resolver.list()
            var found = false
            accounts.forEach { account ->
                val profile = service.current(account.alias)
                if (profile != null) {
                    if (found) output.println()
                    printCurrentProfile(profile, config)
                    found = true
                }
            }
            if (!found) {
                output.info("No managed profiles found in ~/.aws/config. Run 'aws-profile <account>' first.")
            }
        }
        return ExitCodes.SUCCESS
    }

    private fun handleLogin(command: LoginCommand): Int {
        output.info("Starting AWS SSO login for profile '${command.profileName}'...")
        return loginService.login(command.profileName).fold(
            onSuccess = { ExitCodes.SUCCESS },
            onFailure = { e ->
                output.error("Error: ${e.message}")
                ExitCodes.PROCESS_ERROR
            }
        )
    }

    private fun handleValidate(command: ValidateCommand): Int {
        val resolver = AccountResolver(configurationRepository)
        val account = resolver.resolve(command.profileName)
        val awsProfile = awsConfigRepository.getProfile(command.profileName)

        output.info("Validating configuration for '${command.profileName}'...")
        output.info("  Account alias:  ${account.alias}")
        output.info("  Account ID:     ${account.accountId}")
        output.info("  Region:         ${account.region}")

        if (awsProfile != null) {
            output.info("  AWS profile:    found")
            output.info("  Role name:      ${awsProfile.roleName}")
            if (awsProfile.accountId != account.accountId) {
                output.error("  MISMATCH: AWS profile account ID ${awsProfile.accountId} != config ${account.accountId}")
                return ExitCodes.AWS_CONFIG_ERROR
            }
        } else {
            output.info("  AWS profile:    not yet configured (run 'aws-profile ${command.profileName}' to create)")
        }
        output.success("Validation passed.")
        return ExitCodes.SUCCESS
    }

    private fun printProfileConfirmation(profile: AwsProfile, level: AccessLevel) {
        output.success("✓ AWS profile: ${profile.name}")
        output.success("✓ Account:     ${profile.accountId}")
        output.success("✓ Access:      ${level.displayName()}")
        output.success("✓ Permission:  ${profile.roleName}")
        output.success("✓ Region:      ${profile.region}")
    }

    private fun printCurrentProfile(profile: AwsProfile, config: AppConfig) {
        val standingRole = config.standingPermissionSet.value
        val elevatedRole = config.elevatedPermissionSet.value
        val level = when (profile.roleName) {
            standingRole -> "STANDING"
            elevatedRole -> "ELEVATED"
            else -> profile.roleName
        }
        output.info("Profile:     ${profile.name}")
        output.info("Account:     ${profile.accountId}")
        output.info("Access:      $level")
        output.info("Permission:  ${profile.roleName}")
        output.info("Region:      ${profile.region}")
    }

    private fun printHelp() {
        output.info(
            """
            aws-profile $APP_VERSION

            Manage AWS IAM Identity Center profiles for Terraform and the AWS CLI.

            USAGE:
              aws-profile <account>          Switch to standing access
              aws-profile <account>+         Switch to elevated access
              aws-profile list               List configured accounts
              aws-profile list --verbose     List with account IDs and regions
              aws-profile current            Show all managed profiles
              aws-profile current <account>  Show a specific profile
              aws-profile login <account>    Run 'aws sso login --profile <account>'
              aws-profile validate <account> Validate local configuration
              aws-profile version            Show version
              aws-profile --help             Show this help

            EXAMPLES:
              aws-profile prod-1             # standing access
              aws-profile prod-1+            # elevated access
              aws-profile login prod-1       # authenticate via SSO

            CONFIG:
              ~/.config/aws-profile/config.toml

            The AWS CLI profile name never changes — Terraform can permanently use:
              provider "aws" { profile = "prod-1" }
            """.trimIndent()
        )
    }

    private fun exitCodeFor(e: AppError): Int = when (e) {
        is com.argol.awsprofile.errors.ConfigurationError -> ExitCodes.CONFIGURATION_ERROR
        is com.argol.awsprofile.errors.AccountNotFoundError -> ExitCodes.ACCOUNT_NOT_FOUND
        is com.argol.awsprofile.errors.AwsConfigError -> ExitCodes.AWS_CONFIG_ERROR
        is com.argol.awsprofile.errors.ProcessExecutionError -> ExitCodes.PROCESS_ERROR
        is com.argol.awsprofile.errors.ValidationError -> ExitCodes.INVALID_ARGUMENTS
    }
}
