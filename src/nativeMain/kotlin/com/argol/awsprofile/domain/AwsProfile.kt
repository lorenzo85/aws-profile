package com.argol.awsprofile.domain

data class AwsProfile(
    val name: String,
    val ssoSession: String,
    val accountId: String,
    val roleName: String,
    val region: String,
    val output: String = "json"
) {
    fun accessLevel(standingRole: PermissionSetName, elevatedRole: PermissionSetName): AccessLevel? = when (roleName) {
        standingRole.value -> AccessLevel.STANDING
        elevatedRole.value -> AccessLevel.ELEVATED
        else -> null
    }
}
