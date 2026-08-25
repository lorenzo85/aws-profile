package com.argol.awsprofile.application

import com.argol.awsprofile.domain.SsoAccount
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository
import com.argol.awsprofile.ports.SsoDiscovery

class InitService(
    private val ssoDiscovery: SsoDiscovery,
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

        val accounts = ssoDiscovery.discover()

        val ssoSessions = awsConfigRepository.findSsoSessions()
        val ssoSessionName = ssoSessions.firstOrNull()?.name
            ?: awsConfigRepository.listSsoProfiles()
                .mapNotNull { it.ssoSession }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key
            ?: "your-sso-session"
        val ssoRegion = ssoSessions.firstOrNull()?.region ?: "FIXME"

        val regionByAccountId = awsConfigRepository.listSsoProfiles()
            .associateBy({ it.accountId }, { it.region })

        val toml = generateToml(ssoSessionName, ssoRegion, accounts, regionByAccountId)
        configurationRepository.write(toml)
        return toml
    }

    internal fun generateToml(
        ssoSessionName: String,
        ssoRegion: String,
        accounts: List<SsoAccount>,
        regionByAccountId: Map<String, String> = emptyMap()
    ): String {
        val allRoles = accounts.flatMap { it.roles }.distinct().sorted()

        return buildString {
            appendLine("[sso]")
            appendLine("session = \"$ssoSessionName\"")
            appendLine()
            appendLine("[permission_sets]")
            if (allRoles.isNotEmpty()) {
                appendLine("# Roles discovered: ${allRoles.joinToString(", ")}")
            }
            appendLine("# TODO: set the name of your standing (read-only) permission set")
            appendLine("standing = \"FIXME\"")
            appendLine("# TODO: set the name of your elevated (admin) permission set, or remove this line")
            appendLine("# elevated = \"FIXME\"")
            appendLine()
            accounts.forEach { account ->
                val roleInfo = if (account.roles.isNotEmpty()) " | roles: ${account.roles.joinToString(", ")}" else ""
                appendLine("# ${account.accountName}$roleInfo")
                appendLine("[accounts.${account.alias}]")
                appendLine("account_id = \"${account.accountId}\"")
                val region = regionByAccountId[account.accountId]
                if (region != null) {
                    appendLine("region = \"$region\"")
                } else {
                    appendLine("region = \"$ssoRegion\" # verify region")
                }
                appendLine()
            }
        }
    }
}
