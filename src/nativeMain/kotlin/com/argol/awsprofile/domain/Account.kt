package com.argol.awsprofile.domain

import com.argol.awsprofile.errors.ValidationError

data class Account(
    val alias: String,
    val accountId: String,
    val region: String,
    val standingPermissionSet: PermissionSetName? = null,  // overrides global if set
    val elevatedPermissionSet: PermissionSetName? = null   // overrides global if set
) {
    init {
        if (!accountId.matches(Regex("\\d{12}"))) {
            throw ValidationError("Invalid AWS account ID '$accountId' for alias '$alias': must be exactly 12 digits")
        }
        if (alias.isBlank()) throw ValidationError("Account alias must not be blank")
        if (region.isBlank()) throw ValidationError("Region must not be blank for account '$alias'")
    }
}
