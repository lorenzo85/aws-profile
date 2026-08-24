package com.argol.awsprofile.domain

data class DiscoveredSsoProfile(
    val profileName: String,
    val ssoSession: String?,
    val accountId: String,
    val roleName: String,
    val region: String
)
