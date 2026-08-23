package com.argol.awsprofile.domain

data class AppConfig(
    val ssoSession: String,
    val standingPermissionSet: PermissionSetName,
    val elevatedPermissionSet: PermissionSetName,
    val accounts: Map<String, Account>
) {
    fun resolve(alias: String): Account? = accounts[alias]

    fun permissionSet(level: AccessLevel): PermissionSetName = when (level) {
        AccessLevel.STANDING -> standingPermissionSet
        AccessLevel.ELEVATED -> elevatedPermissionSet
    }
}
