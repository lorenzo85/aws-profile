package com.argol.awsprofile.application

import com.argol.awsprofile.domain.AppConfig
import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.domain.PermissionSetName
import com.argol.awsprofile.domain.SsoAccount
import com.argol.awsprofile.domain.SsoSession
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository
import com.argol.awsprofile.ports.SsoDiscovery
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ─── Fakes ───────────────────────────────────────────────────────────────────

class FakeSsoDiscovery(
    private val accounts: List<SsoAccount> = emptyList(),
    private val error: ConfigurationError? = null
) : SsoDiscovery {
    override fun discover(): List<SsoAccount> {
        if (error != null) throw error
        return accounts
    }
}

class FakeAwsConfigRepositoryForInit(
    private val discovered: List<DiscoveredSsoProfile> = emptyList(),
    private val ssoSessions: List<SsoSession> = emptyList()
) : AwsConfigRepository {
    override fun getProfile(name: String): AwsProfile? = null
    override fun upsertProfile(profile: AwsProfile) {}
    override fun upsertProfiles(profiles: List<AwsProfile>) {}
    override fun listSsoProfiles(): List<DiscoveredSsoProfile> = discovered
    override fun findSsoSessions(): List<SsoSession> = ssoSessions
}

class FakeConfigurationRepositoryForInit(
    private val fileExists: Boolean = false
) : ConfigurationRepository {
    var written: String? = null
    override fun exists(): Boolean = fileExists
    override fun write(content: String) { written = content }
    override fun load(): AppConfig = AppConfig(
        ssoSession = "test",
        standingPermissionSet = PermissionSetName("Terraform"),
        elevatedPermissionSet = null,
        accounts = emptyMap()
    )
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun account(
    accountId: String,
    name: String = "Account $accountId",
    alias: String = name.lowercase().replace(" ", "-"),
    roles: List<String> = listOf("Terraform")
) = SsoAccount(accountId = accountId, accountName = name, alias = alias, roles = roles)

private fun session(name: String = "company", region: String = "eu-west-1") =
    SsoSession(name = name, startUrl = "https://company.awsapps.com/start", region = region)

// ─── Tests ────────────────────────────────────────────────────────────────────

class InitServiceTest {

    private val configRepo = FakeConfigurationRepositoryForInit(fileExists = false)

    private fun service(
        accounts: List<SsoAccount> = listOf(account("111111111111")),
        sessions: List<SsoSession> = listOf(session()),
        discovered: List<DiscoveredSsoProfile> = emptyList(),
        fileExists: Boolean = false
    ) = InitService(
        FakeSsoDiscovery(accounts),
        FakeAwsConfigRepositoryForInit(discovered, sessions),
        FakeConfigurationRepositoryForInit(fileExists)
    )

    @Test
    fun `throws ConfigurationError when config already exists`() {
        assertFailsWith<ConfigurationError> { service(fileExists = true).init() }
    }

    @Test
    fun `throws ConfigurationError when SSO discovery fails`() {
        val s = InitService(
            FakeSsoDiscovery(error = ConfigurationError("no token")),
            FakeAwsConfigRepositoryForInit(),
            FakeConfigurationRepositoryForInit()
        )
        assertFailsWith<ConfigurationError> { s.init() }
    }

    @Test
    fun `writes TOML when discovery succeeds`() {
        val repo = FakeConfigurationRepositoryForInit(fileExists = false)
        InitService(FakeSsoDiscovery(listOf(account("111111111111"))), FakeAwsConfigRepositoryForInit(), repo).init()
        assertNotNull(repo.written)
    }

    @Test
    fun `generated TOML uses SSO session name from config`() {
        val s = service(sessions = listOf(session("mycompany")))
        val toml = s.generateToml("mycompany", "eu-west-1", listOf(account("111111111111")))
        assertContains(toml, "session = \"mycompany\"")
    }

    @Test
    fun `generated TOML contains all discovered accounts`() {
        val s = service()
        val toml = s.generateToml("company", "eu-west-1", listOf(
            account("111111111111", name = "Prod", alias = "prod"),
            account("222222222222", name = "Staging", alias = "staging")
        ))
        assertContains(toml, "[accounts.prod]")
        assertContains(toml, "account_id = \"111111111111\"")
        assertContains(toml, "[accounts.staging]")
        assertContains(toml, "account_id = \"222222222222\"")
    }

    @Test
    fun `generated TOML shows discovered roles as comment`() {
        val s = service()
        val toml = s.generateToml("company", "eu-west-1", listOf(
            account("111111111111", roles = listOf("Terraform", "TerraformElevated"))
        ))
        assertContains(toml, "Roles discovered: Terraform, TerraformElevated")
    }

    @Test
    fun `generated TOML contains permission_sets with FIXME`() {
        val s = service()
        val toml = s.generateToml("company", "eu-west-1", listOf(account("111111111111")))
        assertContains(toml, "[permission_sets]")
        assertContains(toml, "standing = \"FIXME\"")
    }

    @Test
    fun `generated TOML uses known region from existing config profile`() {
        val s = service()
        val toml = s.generateToml(
            "company", "eu-west-1",
            listOf(account("111111111111")),
            regionByAccountId = mapOf("111111111111" to "us-east-1")
        )
        assertContains(toml, "region = \"us-east-1\"")
    }

    @Test
    fun `generated TOML falls back to SSO region with comment when account not in existing config`() {
        val s = service()
        val toml = s.generateToml("company", "eu-west-1", listOf(account("111111111111")))
        assertContains(toml, "region = \"eu-west-1\"")
        assertContains(toml, "verify region")
    }

    @Test
    fun `generated TOML has sso before permission_sets before accounts`() {
        val s = service()
        val toml = s.generateToml("company", "eu-west-1", listOf(account("111111111111")))
        assertTrue(toml.indexOf("[sso]") < toml.indexOf("[permission_sets]"))
        assertTrue(toml.indexOf("[permission_sets]") < toml.indexOf("[accounts."))
    }

    @Test
    fun `account name appears as comment above account section`() {
        val s = service()
        val toml = s.generateToml("company", "eu-west-1", listOf(account("111111111111", name = "Production")))
        assertContains(toml, "# Production")
    }
}
