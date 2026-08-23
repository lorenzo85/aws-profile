package com.argol.awsprofile.application

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.ports.AwsConfigRepository

class CurrentProfileService(
    private val awsConfigRepository: AwsConfigRepository
) {
    fun current(profileName: String): AwsProfile? =
        awsConfigRepository.getProfile(profileName)
}
