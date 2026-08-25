package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.domain.SsoAccount
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.ProcessRunner
import com.argol.awsprofile.ports.SsoDiscovery

class AwsSsoDiscovery(
    private val processRunner: ProcessRunner,
    private val cacheReader: SsoCacheReader
) : SsoDiscovery {

    override fun discover(): List<SsoAccount> {
        if (!processRunner.isAvailable("aws")) {
            throw ConfigurationError("AWS CLI not found. Install it first: https://aws.amazon.com/cli/")
        }

        val token = cacheReader.findValidToken()
            ?: throw ConfigurationError(
                "No SSO token found in ~/.aws/sso/cache/.\n" +
                "Log in first with: aws sso login --profile <any-configured-profile>"
            )

        val accounts = listAccounts(token)
        if (accounts.isEmpty()) {
            throw ConfigurationError("No accounts returned from SSO. Check that your SSO token is still valid.")
        }

        return accounts.map { (accountId, accountName) ->
            SsoAccount(
                accountId = accountId,
                accountName = accountName,
                alias = normalizeAlias(accountName),
                roles = listRoles(token, accountId)
            )
        }
    }

    private fun listAccounts(token: SsoToken): List<Pair<String, String>> {
        val result = processRunner.capture(
            "aws", listOf(
                "sso", "list-accounts",
                "--access-token", token.accessToken,
                "--region", token.region,
                "--output", "text",
                "--query", "accountList[*].[accountId,accountName]",
                "--no-paginate",
                "--no-cli-pager"
            )
        )
        if (result.exitCode != 0) {
            val msg = if (result.stderr.contains("expired", ignoreCase = true) ||
                result.stderr.contains("invalid", ignoreCase = true)
            ) {
                "SSO token has expired. Run: aws sso login --profile <any-configured-profile>"
            } else {
                "Failed to list SSO accounts: ${result.stderr}"
            }
            throw ConfigurationError(msg)
        }
        return result.stdout.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size >= 2) parts[0].trim() to parts[1].trim() else null
            }
    }

    private fun listRoles(token: SsoToken, accountId: String): List<String> {
        val result = processRunner.capture(
            "aws", listOf(
                "sso", "list-account-roles",
                "--access-token", token.accessToken,
                "--account-id", accountId,
                "--region", token.region,
                "--output", "text",
                "--query", "roleList[*].roleName",
                "--no-paginate",
                "--no-cli-pager"
            )
        )
        if (result.exitCode != 0) return emptyList()
        return result.stdout.lines().filter { it.isNotBlank() }.map { it.trim() }
    }

    private fun normalizeAlias(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "account" }
}
