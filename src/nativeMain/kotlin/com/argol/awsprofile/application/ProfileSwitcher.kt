package com.argol.awsprofile.application

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

        val permissionSet = config.permissionSet(selection.accessLevel)

        val profile = AwsProfile(
            name = account.alias,
            ssoSession = config.ssoSession,
            accountId = account.accountId,
            roleName = permissionSet.value,
            region = account.region
        )

        awsConfigRepository.upsertProfile(profile)
        return profile
    }
}
