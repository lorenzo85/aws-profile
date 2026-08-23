package com.argol.awsprofile.application

import com.argol.awsprofile.domain.Account
import com.argol.awsprofile.errors.AccountNotFoundError
import com.argol.awsprofile.ports.ConfigurationRepository

class AccountResolver(
    private val configurationRepository: ConfigurationRepository
) {
    fun resolve(alias: String): Account {
        val config = configurationRepository.load()
        return config.resolve(alias) ?: throw AccountNotFoundError(alias)
    }

    fun list(): List<Account> =
        configurationRepository.load().accounts.values.sortedBy { it.alias }
}
