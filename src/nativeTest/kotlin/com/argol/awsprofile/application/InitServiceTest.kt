package com.argol.awsprofile.application

import com.argol.awsprofile.domain.AppConfig
import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.domain.PermissionSetName
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository
import com.argol.awsprofile.domain.AwsProfile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// ─── Fakes ───────────────────────────────────────────────────────────────────

class FakeAwsConfigRepositoryForInit(
    private val discovered: List<DiscoveredSsoProfile> = emptyList()
) : AwsConfigRepository {
    override fun getProfile(name: String): AwsProfile? = null
    override fun upsertProfile(profile: AwsProfile) {}
    override fun upsertProfiles(profiles: List<AwsProfile>) {}
    override fun listSsoProfiles(): List<DiscoveredSsoProfile> = discovered
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

private fun profile(
    name: String,
    accountId: String,
    region: String = "eu-west-1",
    ssoSession: String? = "company",
    roleName: String = "Terraform"
) = DiscoveredSsoProfile(name, ssoSession, accountId, roleName, region)

// ─── Tests ────────────────────────────────────────────────────────────────────

class InitServiceTest {

    @Test
    fun `throws ConfigurationError when config already exists`() {
        val service = InitService(
            FakeAwsConfigRepositoryForInit(),
            FakeConfigurationRepositoryForInit(fileExists = true)
        )
        assertFailsWith<ConfigurationError> { service.init() }
    }

    @Test
    fun `throws ConfigurationError when no SSO profiles found`() {
        val service = InitService(
            FakeAwsConfigRepositoryForInit(emptyList()),
            FakeConfigurationRepositoryForInit(fileExists = false)
        )
        assertFailsWith<ConfigurationError> { service.init() }
    }

    @Test
    fun `writes TOML when config does not exist and profiles are found`() {
        val configRepo = FakeConfigurationRepositoryForInit(fileExists = false)
        val service = InitService(
            FakeAwsConfigRepositoryForInit(listOf(profile("prod-1", "111111111111"))),
            configRepo
        )
        service.init()
        assertTrue(configRepo.written != null)
    }

    @Test
    fun `generated TOML contains sso session from discovered profile`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val toml = service.generateToml(listOf(profile("prod-1", "111111111111", ssoSession = "mycompany")))
        assertContains(toml, "session = \"mycompany\"")
    }

    @Test
    fun `generated TOML uses most common sso session when profiles differ`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val profiles = listOf(
            profile("prod-1", "111111111111", ssoSession = "company"),
            profile("prod-2", "222222222222", ssoSession = "company"),
            profile("legacy", "333333333333", ssoSession = "old-company")
        )
        val toml = service.generateToml(profiles)
        assertContains(toml, "session = \"company\"")
    }

    @Test
    fun `generated TOML contains placeholder session when no sso_session found`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val toml = service.generateToml(listOf(profile("prod-1", "111111111111", ssoSession = null)))
        assertContains(toml, "session = \"your-sso-session\"")
    }

    @Test
    fun `generated TOML contains all discovered accounts`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val toml = service.generateToml(listOf(
            profile("prod-1", "111111111111"),
            profile("prod-2", "222222222222"),
            profile("staging", "333333333333", region = "eu-central-1")
        ))
        assertContains(toml, "[accounts.prod-1]")
        assertContains(toml, "account_id = \"111111111111\"")
        assertContains(toml, "[accounts.prod-2]")
        assertContains(toml, "account_id = \"222222222222\"")
        assertContains(toml, "[accounts.staging]")
        assertContains(toml, "region = \"eu-central-1\"")
    }

    @Test
    fun `generated TOML contains permission_sets section with FIXME placeholders`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val toml = service.generateToml(listOf(profile("prod-1", "111111111111")))
        assertContains(toml, "[permission_sets]")
        assertContains(toml, "standing = \"FIXME\"")
    }

    @Test
    fun `generated TOML has sso section before permission_sets`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val toml = service.generateToml(listOf(profile("prod-1", "111111111111")))
        val ssoIdx = toml.indexOf("[sso]")
        val permIdx = toml.indexOf("[permission_sets]")
        val accountIdx = toml.indexOf("[accounts.")
        assertTrue(ssoIdx < permIdx)
        assertTrue(permIdx < accountIdx)
    }

    @Test
    fun `account region is preserved in generated TOML`() {
        val service = InitService(FakeAwsConfigRepositoryForInit(), FakeConfigurationRepositoryForInit())
        val toml = service.generateToml(listOf(profile("prod-1", "111111111111", region = "us-east-1")))
        assertContains(toml, "region = \"us-east-1\"")
    }
}
