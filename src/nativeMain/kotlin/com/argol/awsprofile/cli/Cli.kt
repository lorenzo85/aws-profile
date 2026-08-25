package com.argol.awsprofile.cli

import com.argol.awsprofile.APP_VERSION
import com.argol.awsprofile.application.AccountResolver
import com.argol.awsprofile.application.CurrentProfileService
import com.argol.awsprofile.application.InitService
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
        is ResetCommand -> handleReset()
        is InitCommand -> handleInit()
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
        val resolver = AccountResolver(awsConfigRepository)
        resolver.list().forEach { output.info(it.profileName) }
        return ExitCodes.SUCCESS
    }

    private fun handleListVerbose(): Int {
        val resolver = AccountResolver(awsConfigRepository)
        val profiles = resolver.list()
        val maxAlias = profiles.maxOfOrNull { it.profileName.length } ?: 0
        profiles.forEach { profile ->
            val pad = " ".repeat(maxAlias - profile.profileName.length)
            output.info("${profile.profileName}$pad    ${profile.accountId}    ${profile.region}")
        }
        return ExitCodes.SUCCESS
    }

    private fun handleCurrent(command: CurrentCommand): Int {
        val config = try { configurationRepository.load() } catch (e: AppError) { null }
        val profileName = command.profileName

        if (profileName != null) {
            val profile = awsConfigRepository.getProfile(profileName)
            if (profile == null) {
                output.error("No configured profile found for: $profileName")
                return ExitCodes.ACCOUNT_NOT_FOUND
            }
            printCurrentProfile(profile, config)
        } else {
            val discovered = awsConfigRepository.listSsoProfiles().sortedBy { it.profileName }
            if (discovered.isEmpty()) {
                output.info("No SSO profiles found in ~/.aws/config. Run 'aws-profile <account>' first.")
                return ExitCodes.SUCCESS
            }
            var first = true
            discovered.forEach { d ->
                val profile = awsConfigRepository.getProfile(d.profileName)
                if (profile != null) {
                    if (!first) output.println()
                    printCurrentProfile(profile, config)
                    first = false
                }
            }
        }
        return ExitCodes.SUCCESS
    }

    private fun handleInit(): Int {
        InitService(configurationRepository).init()
        output.success("Config written to ~/.config/aws-profile/config.toml")
        output.info("Edit elevated_suffix if your elevated role uses a different suffix.")
        return ExitCodes.SUCCESS
    }

    private fun handleReset(): Int {
        val switcher = ProfileSwitcher(configurationRepository, awsConfigRepository)
        val profiles = switcher.resetAll()
        output.info("Reset ${profiles.size} profile(s) to standing access.")
        profiles.sortedBy { it.name }.forEach { profile ->
            output.info("  ✓ ${profile.name}  →  ${profile.roleName}")
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
        output.info("Validating configuration for '${command.profileName}'...")

        val profile = awsConfigRepository.getProfile(command.profileName)
        if (profile == null) {
            output.info("  AWS profile:    not yet configured (run 'aws-profile ${command.profileName}' to create)")
            return ExitCodes.ACCOUNT_NOT_FOUND
        }

        output.info("  Account ID:     ${profile.accountId}")
        output.info("  Region:         ${profile.region}")
        output.info("  SSO session:    ${profile.ssoSession}")
        output.info("  Role name:      ${profile.roleName}")
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

    private fun printCurrentProfile(profile: AwsProfile, config: AppConfig?) {
        val suffix = config?.elevatedSuffix
        val level = when {
            suffix != null && profile.roleName.endsWith(suffix) -> "ELEVATED"
            suffix != null -> "STANDING"
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
              aws-profile init               Create config file with default settings
              aws-profile <account>          Switch to standing access
              aws-profile <account>+         Switch to elevated access
              aws-profile list               List SSO profiles from ~/.aws/config
              aws-profile list --verbose     List with account IDs and regions
              aws-profile current            Show all SSO profile details
              aws-profile current <account>  Show a specific profile
              aws-profile login <account>    Run 'aws sso login --profile <account>'
              aws-profile validate <account> Validate profile configuration
              aws-profile reset              Reset all profiles to standing access
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
