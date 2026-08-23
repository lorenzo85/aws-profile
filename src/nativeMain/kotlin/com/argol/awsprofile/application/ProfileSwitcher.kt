package com.argol.awsprofile.application

import com.argol.awsprofile.domain.AccessLevel
import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.ProfileSelection
import com.argol.awsprofile.errors.AccountNotFoundError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository

class ProfileSwitcher(
    private val configurationRepository: ConfigurationRepository,
    private val awsConfigRepository: AwsConfigRepository
) {
    fun switch(selection: ProfileSelection): AwsProfile {
        val config = configurationRepository.load()

        if (config.resolve(selection.accountAlias) == null) {
            throw AccountNotFoundError(selection.accountAlias)
        }

        // Build a profile for every account in the TOML config.
        // The target account gets the requested access level; all others get standing.
        // Written in a single atomic pass so ~/.aws/config always mirrors the full config.
        val profiles = config.accounts.values.map { account ->
            val level = if (account.alias == selection.accountAlias) selection.accessLevel
                        else AccessLevel.STANDING
            AwsProfile(
                name = account.alias,
                ssoSession = config.ssoSession,
                accountId = account.accountId,
                roleName = config.permissionSet(level).value,
                region = account.region
            )
        }

        awsConfigRepository.upsertProfiles(profiles)

        return profiles.first { it.name == selection.accountAlias }
    }
}
