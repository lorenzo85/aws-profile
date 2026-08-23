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

        val account = config.resolve(selection.accountAlias)
            ?: throw AccountNotFoundError(selection.accountAlias)

        // Build profiles for all accounts defined in the TOML config so that
        // ~/.aws/config always contains the full set. For accounts that are
        // already present in ~/.aws/config their existing role name is preserved,
        // allowing multiple accounts to be elevated independently. Accounts that
        // are not yet present are initialised with standing access.
        val profiles = config.accounts.values.map { acc ->
            if (acc.alias == selection.accountAlias) {
                AwsProfile(
                    name = acc.alias,
                    ssoSession = config.ssoSession,
                    accountId = acc.accountId,
                    roleName = config.permissionSet(acc, selection.accessLevel).value,
                    region = acc.region
                )
            } else {
                // Keep existing role if the account is already configured,
                // otherwise default to the effective standing permission set for that account.
                val existing = awsConfigRepository.getProfile(acc.alias)
                AwsProfile(
                    name = acc.alias,
                    ssoSession = config.ssoSession,
                    accountId = acc.accountId,
                    roleName = existing?.roleName ?: config.permissionSet(acc, AccessLevel.STANDING).value,
                    region = acc.region
                )
            }
        }

        awsConfigRepository.upsertProfiles(profiles)

        return profiles.first { it.name == selection.accountAlias }
    }
}
