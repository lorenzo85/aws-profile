package com.argol.awsprofile.domain

import com.argol.awsprofile.errors.ValidationError

data class PermissionSetName(val value: String) {
    init {
        if (value.isBlank()) throw ValidationError("Permission set name must not be blank")
    }

    override fun toString(): String = value
}
