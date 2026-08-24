package com.argol.awsprofile.application

import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository

class InitService(
    private val awsConfigRepository: AwsConfigRepository,
    private val configurationRepository: ConfigurationRepository
) {
    fun init(): String {
        if (configurationRepository.exists()) {
            throw ConfigurationError(
                "Config already exists. Delete it first or edit it manually:\n" +
                "  ~/.config/aws-profile/config.toml"
            )
        }

        val profiles = awsConfigRepository.listSsoProfiles()
        if (profiles.isEmpty()) {
            throw ConfigurationError(
                "No SSO profiles found in ~/.aws/config.\n" +
                "Set up your profiles first with 'aws configure sso', then re-run 'aws-profile init'."
            )
        }

        val toml = generateToml(profiles)
        configurationRepository.write(toml)
        return toml
    }

    internal fun generateToml(profiles: List<DiscoveredSsoProfile>): String {
        val ssoSession = profiles
            .mapNotNull { it.ssoSession }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "your-sso-session"

        return buildString {
            appendLine("[sso]")
            appendLine("session = \"$ssoSession\"")
            appendLine()
            appendLine("[permission_sets]")
            appendLine("# TODO: set the name of your standing (read-only) permission set")
            appendLine("standing = \"FIXME\"")
            appendLine("# TODO: set the name of your elevated (admin) permission set, or remove this line")
            appendLine("# elevated = \"FIXME\"")
            appendLine()
            profiles.forEach { profile ->
                appendLine("[accounts.${profile.profileName}]")
                appendLine("account_id = \"${profile.accountId}\"")
                appendLine("region = \"${profile.region}\"")
                appendLine()
            }
        }
    }
}
