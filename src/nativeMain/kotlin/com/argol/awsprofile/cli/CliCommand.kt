package com.argol.awsprofile.cli

import com.argol.awsprofile.domain.ProfileSelection

sealed interface CliCommand

data class SwitchCommand(val selection: ProfileSelection) : CliCommand
data object ListCommand : CliCommand
data class ListVerboseCommand(val verbose: Boolean = true) : CliCommand
data class CurrentCommand(val profileName: String? = null) : CliCommand
data class LoginCommand(val profileName: String) : CliCommand
data class ValidateCommand(val profileName: String) : CliCommand
data object ResetCommand : CliCommand
data object InitCommand : CliCommand
data object VersionCommand : CliCommand
data object HelpCommand : CliCommand
