package com.argol.awsprofile.domain

import com.argol.awsprofile.errors.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountTest {

    @Test
    fun `valid account is created successfully`() {
        val account = Account(alias = "prod-1", accountId = "111111111111", region = "eu-west-1")
        assertEquals("prod-1", account.alias)
        assertEquals("111111111111", account.accountId)
        assertEquals("eu-west-1", account.region)
    }

    @Test
    fun `account ID with fewer than 12 digits is rejected`() {
        assertFailsWith<ValidationError> {
            Account(alias = "prod-1", accountId = "11111", region = "eu-west-1")
        }
    }

    @Test
    fun `account ID with more than 12 digits is rejected`() {
        assertFailsWith<ValidationError> {
            Account(alias = "prod-1", accountId = "1111111111111", region = "eu-west-1")
        }
    }

    @Test
    fun `account ID with letters is rejected`() {
        assertFailsWith<ValidationError> {
            Account(alias = "prod-1", accountId = "11111111111X", region = "eu-west-1")
        }
    }

    @Test
    fun `blank alias is rejected`() {
        assertFailsWith<ValidationError> {
            Account(alias = "   ", accountId = "111111111111", region = "eu-west-1")
        }
    }

    @Test
    fun `blank region is rejected`() {
        assertFailsWith<ValidationError> {
            Account(alias = "prod-1", accountId = "111111111111", region = "")
        }
    }
}
