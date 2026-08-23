package com.argol.awsprofile.domain

data class AppConfig(
    val ssoSession: String,
    val standingPermissionSet: PermissionSetName,
    val elevatedPermissionSet: PermissionSetName,
    val accounts: Map<String, Account>
) {
    fun resolve(alias: String): Account? = accounts[alias]

    // Returns the effective permission set for a given account and access level.
    // Per-account overrides take precedence over the global permission_sets.
    fun permissionSet(account: Account, level: AccessLevel): PermissionSetName = when (level) {
        AccessLevel.STANDING -> account.standingPermissionSet ?: standingPermissionSet
        AccessLevel.ELEVATED -> account.elevatedPermissionSet ?: elevatedPermissionSet
    }

    // Convenience overload using the global sets (no per-account override).
    fun permissionSet(level: AccessLevel): PermissionSetName = when (level) {
        AccessLevel.STANDING -> standingPermissionSet
        AccessLevel.ELEVATED -> elevatedPermissionSet
    }
}
