package com.argol.awsprofile.application

import com.argol.awsprofile.domain.*
import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.errors.AccountNotFoundError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// ─── Fakes ───────────────────────────────────────────────────────────────────

class FakeConfigurationRepository(private val config: AppConfig) : ConfigurationRepository {
    override fun load(): AppConfig = config
    override fun exists(): Boolean = true
    override fun write(content: String) {}
}

class FakeAwsConfigRepository : AwsConfigRepository {
    val profiles = mutableMapOf<String, AwsProfile>()

    override fun getProfile(name: String): AwsProfile? = profiles[name]
    override fun upsertProfile(profile: AwsProfile) { profiles[profile.name] = profile }
    override fun upsertProfiles(profiles: List<AwsProfile>) { profiles.forEach { upsertProfile(it) } }
    override fun listSsoProfiles(): List<DiscoveredSsoProfile> = emptyList()
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun makeConfig(vararg accounts: Pair<String, String>) = AppConfig(
    ssoSession = "company",
    standingPermissionSet = PermissionSetName("Terraform"),
    elevatedPermissionSet = PermissionSetName("TerraformElevated"),  // present by default
    accounts = accounts.associate { (alias, id) ->
        alias to Account(alias = alias, accountId = id, region = "eu-west-1")
    }
)

// ─── Tests ────────────────────────────────────────────────────────────────────

class ProfileSwitcherTest {

    private val config = makeConfig("prod-1" to "111111111111", "prod-2" to "222222222222")
    private val awsRepo = FakeAwsConfigRepository()
    private val switcher = ProfileSwitcher(FakeConfigurationRepository(config), awsRepo)

    @Test
    fun `standing switch writes Terraform role`() {
        val profile = switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        assertEquals("prod-1", profile.name)
        assertEquals("Terraform", profile.roleName)
        assertEquals("111111111111", profile.accountId)
        assertEquals("company", profile.ssoSession)
        assertEquals("eu-west-1", profile.region)
    }

    @Test
    fun `elevated switch writes TerraformElevated role`() {
        val profile = switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("TerraformElevated", profile.roleName)
    }

    @Test
    fun `profile name is always the account alias without plus`() {
        val profile = switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("prod-1", profile.name)
    }

    @Test
    fun `switch persists profile to repository`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        val persisted = awsRepo.profiles["prod-1"]
        assertNotNull(persisted)
        assertEquals("Terraform", persisted.roleName)
    }

    @Test
    fun `unknown account throws AccountNotFoundError`() {
        assertFailsWith<AccountNotFoundError> {
            switcher.switch(ProfileSelection("prod-99", AccessLevel.STANDING))
        }
    }

    @Test
    fun `switch updates existing profile in repository`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("TerraformElevated", awsRepo.profiles["prod-1"]?.roleName)
    }

    @Test
    fun `standing and elevated produce different role names`() {
        val standing = switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        val elevated = switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assert(standing.roleName != elevated.roleName)
    }

    @Test
    fun `switching different accounts does not overwrite each other`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        switcher.switch(ProfileSelection("prod-2", AccessLevel.ELEVATED))
        assertEquals("Terraform", awsRepo.profiles["prod-1"]?.roleName)
        assertEquals("TerraformElevated", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `all accounts are written on every switch`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        // prod-2 must also be present, defaulting to standing as it was not previously configured
        assertNotNull(awsRepo.profiles["prod-2"])
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `non-target accounts preserve their existing role`() {
        // Elevate prod-2 first
        switcher.switch(ProfileSelection("prod-2", AccessLevel.ELEVATED))
        assertEquals("TerraformElevated", awsRepo.profiles["prod-2"]?.roleName)

        // Switching prod-1 must NOT reset prod-2 back to standing
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("TerraformElevated", awsRepo.profiles["prod-1"]?.roleName)
        assertEquals("TerraformElevated", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `unconfigured non-target accounts default to standing`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `elevating an account with no elevated permission set throws ConfigurationError`() {
        val standingOnlyConfig = AppConfig(
            ssoSession = "company",
            standingPermissionSet = PermissionSetName("Terraform"),
            elevatedPermissionSet = null,
            accounts = mapOf("prod-1" to Account("prod-1", "111111111111", "eu-west-1"))
        )
        val s = ProfileSwitcher(FakeConfigurationRepository(standingOnlyConfig), FakeAwsConfigRepository())
        assertFailsWith<com.argol.awsprofile.errors.ConfigurationError> {
            s.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        }
    }

    @Test
    fun `per-account elevated override is used when present`() {
        val configWithOverride = AppConfig(
            ssoSession = "company",
            standingPermissionSet = PermissionSetName("Terraform"),
            elevatedPermissionSet = PermissionSetName("TerraformElevated"),
            accounts = mapOf(
                "prod-1" to Account(
                    alias = "prod-1",
                    accountId = "111111111111",
                    region = "eu-west-1",
                    elevatedPermissionSet = PermissionSetName("InfraOperatorAdmin")
                )
            )
        )
        val repo = FakeAwsConfigRepository()
        ProfileSwitcher(FakeConfigurationRepository(configWithOverride), repo)
            .switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("InfraOperatorAdmin", repo.profiles["prod-1"]?.roleName)
    }

    @Test
    fun `resetAll sets all accounts to standing`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        switcher.switch(ProfileSelection("prod-2", AccessLevel.ELEVATED))
        switcher.resetAll()
        assertEquals("Terraform", awsRepo.profiles["prod-1"]?.roleName)
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)
    }
}
