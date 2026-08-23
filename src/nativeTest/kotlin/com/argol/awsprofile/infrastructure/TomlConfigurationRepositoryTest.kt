package com.argol.awsprofile.infrastructure

import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.infrastructure.config.TomlConfigurationRepository
import com.argol.awsprofile.infrastructure.filesystem.FileSystem
import com.argol.awsprofile.infrastructure.filesystem.NativeFileSystem
import com.argol.awsprofile.infrastructure.filesystem.NativeUserDirectories
import com.argol.awsprofile.infrastructure.filesystem.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TomlConfigurationRepositoryTest {

    private val repo = TomlConfigurationRepository(NativeFileSystem(), NativeUserDirectories())

    private val validToml = """
        [sso]
        session = "company"

        [permission_sets]
        standing = "Terraform"
        elevated = "TerraformElevated"

        [accounts.prod-1]
        account_id = "111111111111"
        region = "eu-west-1"

        [accounts.prod-2]
        account_id = "222222222222"
        region = "eu-west-1"

        [accounts.prod-3]
        account_id = "333333333333"
        region = "eu-central-1"
    """.trimIndent()

    @Test
    fun `parses valid TOML config correctly`() {
        val config = repo.parse(validToml)
        assertEquals("company", config.ssoSession)
        assertEquals("Terraform", config.standingPermissionSet.value)
        assertEquals("TerraformElevated", config.elevatedPermissionSet.value)
        assertEquals(3, config.accounts.size)
    }

    @Test
    fun `resolves account by alias`() {
        val config = repo.parse(validToml)
        val account = config.resolve("prod-1")
        assertNotNull(account)
        assertEquals("111111111111", account.accountId)
        assertEquals("eu-west-1", account.region)
    }

    @Test
    fun `returns null for unknown alias`() {
        val config = repo.parse(validToml)
        val account = config.resolve("prod-99")
        assertEquals(null, account)
    }

    @Test
    fun `throws ConfigurationError when sso section missing`() {
        val toml = """
            [permission_sets]
            standing = "Terraform"
            elevated = "TerraformElevated"

            [accounts.prod-1]
            account_id = "111111111111"
            region = "eu-west-1"
        """.trimIndent()
        assertFailsWith<ConfigurationError> { repo.parse(toml) }
    }

    @Test
    fun `throws ConfigurationError when permission_sets missing`() {
        val toml = """
            [sso]
            session = "company"

            [accounts.prod-1]
            account_id = "111111111111"
            region = "eu-west-1"
        """.trimIndent()
        assertFailsWith<ConfigurationError> { repo.parse(toml) }
    }

    @Test
    fun `throws ConfigurationError when no accounts defined`() {
        val toml = """
            [sso]
            session = "company"

            [permission_sets]
            standing = "Terraform"
            elevated = "TerraformElevated"
        """.trimIndent()
        assertFailsWith<ConfigurationError> { repo.parse(toml) }
    }

    @Test
    fun `handles unquoted values`() {
        val toml = """
            [sso]
            session = company

            [permission_sets]
            standing = Terraform
            elevated = TerraformElevated

            [accounts.prod-1]
            account_id = 111111111111
            region = eu-west-1
        """.trimIndent()
        val config = repo.parse(toml)
        assertEquals("company", config.ssoSession)
        assertEquals("Terraform", config.standingPermissionSet.value)
    }

    @Test
    fun `ignores comment lines`() {
        val toml = """
            # This is a comment
            [sso]
            # SSO session name
            session = "company"

            [permission_sets]
            standing = "Terraform"
            elevated = "TerraformElevated"

            # Accounts below
            [accounts.prod-1]
            account_id = "111111111111"
            region = "eu-west-1"
        """.trimIndent()
        val config = repo.parse(toml)
        assertEquals("company", config.ssoSession)
    }

    @Test
    fun `handles 60 accounts`() {
        val accountsToml = (1..60).joinToString("\n\n") { i ->
            val id = i.toString().padStart(12, '0')
            "[accounts.prod-$i]\naccount_id = \"$id\"\nregion = \"eu-west-1\""
        }
        val toml = """
            [sso]
            session = "company"

            [permission_sets]
            standing = "Terraform"
            elevated = "TerraformElevated"

        """.trimIndent() + "\n" + accountsToml
        val config = repo.parse(toml)
        assertEquals(60, config.accounts.size)
    }
}
