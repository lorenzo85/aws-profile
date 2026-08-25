package com.argol.awsprofile.application

import com.argol.awsprofile.domain.*
import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.errors.AccountNotFoundError
import com.argol.awsprofile.ports.AwsConfigRepository
import com.argol.awsprofile.ports.ConfigurationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    override fun listSsoProfiles(): List<DiscoveredSsoProfile> = profiles.values.map {
        DiscoveredSsoProfile(it.name, it.ssoSession, it.accountId, it.roleName, it.region)
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private val defaultConfig = AppConfig(elevatedSuffix = "Elevated")

private fun profile(alias: String, accountId: String, role: String = "Terraform") =
    AwsProfile(name = alias, ssoSession = "company", accountId = accountId, roleName = role, region = "eu-west-1")

// ─── Tests ────────────────────────────────────────────────────────────────────

class ProfileSwitcherTest {

    private val awsRepo = FakeAwsConfigRepository().also {
        it.upsertProfile(profile("prod-1", "111111111111"))
        it.upsertProfile(profile("prod-2", "222222222222"))
    }
    private val switcher = ProfileSwitcher(FakeConfigurationRepository(defaultConfig), awsRepo)

    @Test
    fun `standing switch keeps base role`() {
        val result = switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        assertEquals("Terraform", result.roleName)
    }

    @Test
    fun `elevated switch appends suffix`() {
        val result = switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("TerraformElevated", result.roleName)
    }

    @Test
    fun `elevated switch on already-elevated profile produces correct role`() {
        awsRepo.upsertProfile(profile("prod-1", "111111111111", "TerraformElevated"))
        val result = switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("TerraformElevated", result.roleName)
    }

    @Test
    fun `standing switch on elevated profile strips suffix`() {
        awsRepo.upsertProfile(profile("prod-1", "111111111111", "TerraformElevated"))
        val result = switcher.switch(ProfileSelection("prod-1", AccessLevel.STANDING))
        assertEquals("Terraform", result.roleName)
    }

    @Test
    fun `profile metadata is preserved on switch`() {
        val result = switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("prod-1", result.name)
        assertEquals("111111111111", result.accountId)
        assertEquals("company", result.ssoSession)
        assertEquals("eu-west-1", result.region)
    }

    @Test
    fun `unknown account throws AccountNotFoundError`() {
        assertFailsWith<AccountNotFoundError> {
            switcher.switch(ProfileSelection("prod-99", AccessLevel.STANDING))
        }
    }

    @Test
    fun `switching one account does not affect another`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `custom suffix is used`() {
        val config = AppConfig(elevatedSuffix = "Admin")
        val repo = FakeAwsConfigRepository().also { it.upsertProfile(profile("prod-1", "111111111111", "Operator")) }
        val result = ProfileSwitcher(FakeConfigurationRepository(config), repo)
            .switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("OperatorAdmin", result.roleName)
    }

    @Test
    fun `resetAll strips suffix from all profiles`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        switcher.switch(ProfileSelection("prod-2", AccessLevel.ELEVATED))
        switcher.resetAll()
        assertEquals("Terraform", awsRepo.profiles["prod-1"]?.roleName)
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `resetAll is a no-op for profiles already at standing`() {
        val profiles = switcher.resetAll()
        assertEquals("Terraform", awsRepo.profiles["prod-1"]?.roleName)
        assertEquals(2, profiles.size)
    }
}
