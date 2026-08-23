package com.argol.awsprofile.domain

data class AppConfig(
    val ssoSession: String,
    val standingPermissionSet: PermissionSetName,
    val elevatedPermissionSet: PermissionSetName?,   // null = no elevated access globally
    val accounts: Map<String, Account>
) {
    fun resolve(alias: String): Account? = accounts[alias]

    // Returns the effective permission set for the given account and level.
    // Per-account overrides take precedence over the global permission_sets.
    // Returns null if elevated is requested but not configured for this account.
    fun permissionSet(account: Account, level: AccessLevel): PermissionSetName? = when (level) {
        AccessLevel.STANDING -> account.standingPermissionSet ?: standingPermissionSet
        AccessLevel.ELEVATED -> account.elevatedPermissionSet ?: elevatedPermissionSet
    }
}
