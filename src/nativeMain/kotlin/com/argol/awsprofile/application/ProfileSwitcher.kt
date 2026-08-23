package com.argol.awsprofile.application

import com.argol.awsprofile.domain.AccessLevel
import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.ProfileSelection
import com.argol.awsprofile.errors.AccountNotFoundError
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository

class ProfileSwitcher(
    private val configurationRepository: ConfigurationRepository,
    private val awsConfigRepository: AwsConfigRepository
) {
    fun switch(selection: ProfileSelection): AwsProfile {
        val config = configurationRepository.load()

        val account = config.resolve(selection.accountAlias)
            ?: throw AccountNotFoundError(selection.accountAlias)

        val targetRole = config.permissionSet(account, selection.accessLevel)
            ?: throw ConfigurationError(
                "Account '${account.alias}' does not have an elevated permission set configured. " +
                "Add 'elevated' to [accounts.${account.alias}] or to [permission_sets] in your config."
            )

        val profiles = config.accounts.values.map { acc ->
            if (acc.alias == selection.accountAlias) {
                AwsProfile(
                    name = acc.alias,
                    ssoSession = config.ssoSession,
                    accountId = acc.accountId,
                    roleName = targetRole.value,
                    region = acc.region
                )
            } else {
                val existing = awsConfigRepository.getProfile(acc.alias)
                val standingRole = config.permissionSet(acc, AccessLevel.STANDING)!!
                AwsProfile(
                    name = acc.alias,
                    ssoSession = config.ssoSession,
                    accountId = acc.accountId,
                    roleName = existing?.roleName ?: standingRole.value,
                    region = acc.region
                )
            }
        }

        awsConfigRepository.upsertProfiles(profiles)
        return profiles.first { it.name == selection.accountAlias }
    }

    fun resetAll(): List<AwsProfile> {
        val config = configurationRepository.load()

        val profiles = config.accounts.values.map { acc ->
            val standingRole = config.permissionSet(acc, AccessLevel.STANDING)!!
            AwsProfile(
                name = acc.alias,
                ssoSession = config.ssoSession,
                accountId = acc.accountId,
                roleName = standingRole.value,
                region = acc.region
            )
        }

        awsConfigRepository.upsertProfiles(profiles)
        return profiles
    }
}
