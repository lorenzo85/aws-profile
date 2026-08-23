package com.argol.awsprofile.ports

import com.argol.awsprofile.domain.AwsProfile

interface AwsConfigRepository {
    fun getProfile(name: String): AwsProfile?
    fun upsertProfile(profile: AwsProfile)
}
