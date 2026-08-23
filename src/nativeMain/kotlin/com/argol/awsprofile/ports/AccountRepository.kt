package com.argol.awsprofile.ports

import com.argol.awsprofile.domain.Account

interface AccountRepository {
    fun find(alias: String): Account?
    fun list(): List<Account>
}
