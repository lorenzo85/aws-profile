package com.argol.awsprofile.application

import com.argol.awsprofile.domain.*
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
}

class FakeAwsConfigRepository : AwsConfigRepository {
    val profiles = mutableMapOf<String, AwsProfile>()

    override fun getProfile(name: String): AwsProfile? = profiles[name]
    override fun upsertProfile(profile: AwsProfile) { profiles[profile.name] = profile }
    override fun upsertProfiles(profiles: List<AwsProfile>) { profiles.forEach { upsertProfile(it) } }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun makeConfig(vararg accounts: Pair<String, String>) = AppConfig(
    ssoSession = "company",
    standingPermissionSet = PermissionSetName("Terraform"),
    elevatedPermissionSet = PermissionSetName("TerraformElevated"),
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
        // prod-2 must also be present even though it was not the target
        assertNotNull(awsRepo.profiles["prod-2"])
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)
    }

    @Test
    fun `non-target accounts always get standing access`() {
        switcher.switch(ProfileSelection("prod-1", AccessLevel.ELEVATED))
        assertEquals("Terraform", awsRepo.profiles["prod-2"]?.roleName)

        switcher.switch(ProfileSelection("prod-2", AccessLevel.ELEVATED))
        // prod-1 reverts to standing when prod-2 is the target
        assertEquals("Terraform", awsRepo.profiles["prod-1"]?.roleName)
        assertEquals("TerraformElevated", awsRepo.profiles["prod-2"]?.roleName)
    }
}
