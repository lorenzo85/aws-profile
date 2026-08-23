package com.argol.awsprofile.cli

import com.argol.awsprofile.domain.AccessLevel
import com.argol.awsprofile.domain.ProfileSelection
import com.argol.awsprofile.errors.ValidationError

class CliParser {

    fun parse(args: Array<String>): CliCommand {
        if (args.isEmpty()) return HelpCommand

        return when (val first = args[0]) {
            "--help", "-h", "help" -> HelpCommand
            "--version", "-v", "version" -> VersionCommand
            "list" -> if (args.getOrNull(1) == "--verbose" || args.getOrNull(1) == "-v") {
                ListVerboseCommand()
            } else {
                ListCommand
            }
            "current" -> CurrentCommand(args.getOrNull(1))
            "login" -> {
                val profile = args.getOrNull(1)
                    ?: throw ValidationError("Usage: aws-profile login <account>")
                LoginCommand(profile)
            }
            "reset" -> ResetCommand
            "validate" -> {
                val profile = args.getOrNull(1)
                    ?: throw ValidationError("Usage: aws-profile validate <account>")
                ValidateCommand(profile)
            }
            else -> parseSwitchCommand(first)
        }
    }

    private fun parseSwitchCommand(arg: String): SwitchCommand {
        if (arg.isBlank()) throw ValidationError("Account name must not be empty")
        if (arg.startsWith("+")) throw ValidationError("Invalid account name: '$arg'")

        return when {
            arg.endsWith("++") ->
                throw ValidationError(
                    "Invalid argument: '$arg'. Use '<account>' for standing or '<account>+' for elevated access."
                )
            arg.endsWith("+") -> {
                val alias = arg.dropLast(1)
                if (alias.isBlank()) throw ValidationError("Account name must not be empty")
                SwitchCommand(ProfileSelection(accountAlias = alias, accessLevel = AccessLevel.ELEVATED))
            }
            else ->
                SwitchCommand(ProfileSelection(accountAlias = arg, accessLevel = AccessLevel.STANDING))
        }
    }
}
