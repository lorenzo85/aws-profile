package com.argol.awsprofile.ports

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.DiscoveredSsoProfile

interface AwsConfigRepository {
    fun getProfile(name: String): AwsProfile?
    fun upsertProfile(profile: AwsProfile)
    fun upsertProfiles(profiles: List<AwsProfile>)
    fun listSsoProfiles(): List<DiscoveredSsoProfile>
}
