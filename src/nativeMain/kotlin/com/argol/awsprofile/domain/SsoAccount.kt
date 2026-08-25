package com.argol.awsprofile.domain

data class SsoAccount(
    val accountId: String,
    val accountName: String,
    val alias: String,
    val roles: List<String>
)
