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
        val existing = awsConfigRepository.getProfile(selection.accountAlias)
            ?: throw AccountNotFoundError(selection.accountAlias)

        val baseRole = existing.roleName.removeSuffix(config.elevatedSuffix)
        val roleName = when (selection.accessLevel) {
            AccessLevel.STANDING -> baseRole
            AccessLevel.ELEVATED -> baseRole + config.elevatedSuffix
        }

        val profile = existing.copy(roleName = roleName)
        awsConfigRepository.upsertProfile(profile)
        return profile
    }

    fun resetAll(): List<AwsProfile> {
        val config = configurationRepository.load()
        val profiles = awsConfigRepository.listSsoProfiles().mapNotNull { discovered ->
            val existing = awsConfigRepository.getProfile(discovered.profileName) ?: return@mapNotNull null
            existing.copy(roleName = existing.roleName.removeSuffix(config.elevatedSuffix))
        }
        awsConfigRepository.upsertProfiles(profiles)
        return profiles
    }
}
