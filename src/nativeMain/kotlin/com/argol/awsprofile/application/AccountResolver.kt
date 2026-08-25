package com.argol.awsprofile.application

import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.errors.AccountNotFoundError
import com.argol.awsprofile.ports.AwsConfigRepository

class AccountResolver(private val awsConfigRepository: AwsConfigRepository) {
    fun resolve(alias: String): DiscoveredSsoProfile =
        awsConfigRepository.listSsoProfiles().find { it.profileName == alias }
            ?: throw AccountNotFoundError(alias)

    fun list(): List<DiscoveredSsoProfile> =
        awsConfigRepository.listSsoProfiles().sortedBy { it.profileName }
}
