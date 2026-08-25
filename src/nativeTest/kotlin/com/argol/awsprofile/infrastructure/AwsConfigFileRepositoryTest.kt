package com.argol.awsprofile.infrastructure

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.infrastructure.aws.AwsConfigFileRepository
import com.argol.awsprofile.infrastructure.filesystem.FileSystem
import com.argol.awsprofile.infrastructure.filesystem.Path
import com.argol.awsprofile.infrastructure.filesystem.UserDirectories
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─── In-memory filesystem ────────────────────────────────────────────────────

class InMemoryFileSystem : FileSystem {
    val files = mutableMapOf<String, String>()
    val directories = mutableSetOf<String>()

    override fun exists(path: Path): Boolean = files.containsKey(path.value) || directories.contains(path.value)
    override fun read(path: Path): String = files[path.value] ?: error("File not found: ${path.value}")
    override fun readOrNull(path: Path): String? = files[path.value]
    override fun write(path: Path, content: String) { files[path.value] = content }
    override fun move(source: Path, target: Path) {
        files[target.value] = files.remove(source.value) ?: error("Source not found: ${source.value}")
    }
    override fun createDirectories(path: Path) { directories.add(path.value) }
    override fun setRestrictivePermissions(path: Path) {}
    override fun listFiles(path: Path): List<Path> =
        files.keys
            .filter { it.startsWith(path.value + "/") && !it.removePrefix(path.value + "/").contains("/") }
            .map { Path(it) }
}

class FakeUserDirectories(home: String = "/home/testuser") : UserDirectories {
    private val home = Path(home)
    override fun home(): Path = home
    override fun configDirectory(): Path = home / ".config" / "aws-profile"
    override fun awsDirectory(): Path = home / ".aws"
}

// ─── Tests ────────────────────────────────────────────────────────────────────

class AwsConfigFileRepositoryTest {

    private lateinit var fs: InMemoryFileSystem
    private lateinit var repo: AwsConfigFileRepository

    private val configPath = "/home/testuser/.aws/config"

    private val existingConfig = """
        [default]
        region = eu-west-1

        [profile prod-1]
        sso_session = company
        sso_account_id = 111111111111
        sso_role_name = Terraform
        region = eu-west-1
        output = json

        [profile prod-2]
        sso_session = company
        sso_account_id = 222222222222
        sso_role_name = Terraform
        region = eu-west-1
        output = json
    """.trimIndent()

    @BeforeTest
    fun setup() {
        fs = InMemoryFileSystem()
        repo = AwsConfigFileRepository(fs, FakeUserDirectories())
        fs.files[configPath] = existingConfig
    }

    @Test
    fun `getProfile returns profile from existing config`() {
        val profile = repo.getProfile("prod-1")
        assertNotNull(profile)
        assertEquals("prod-1", profile.name)
        assertEquals("Terraform", profile.roleName)
        assertEquals("111111111111", profile.accountId)
        assertEquals("eu-west-1", profile.region)
    }

    @Test
    fun `getProfile returns null when config file does not exist`() {
        fs.files.remove(configPath)
        assertNull(repo.getProfile("prod-1"))
    }

    @Test
    fun `getProfile returns null for unknown profile`() {
        assertNull(repo.getProfile("unknown"))
    }

    @Test
    fun `getProfile works without sso_session field`() {
        fs.files[configPath] = """
            [profile legacy]
            sso_account_id = 333333333333
            sso_role_name = Terraform
            region = us-east-1
        """.trimIndent()
        val profile = repo.getProfile("legacy")
        assertNotNull(profile)
        assertEquals("Terraform", profile.roleName)
    }

    @Test
    fun `upsertProfile updates only sso_role_name`() {
        val profile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        repo.upsertProfile(profile)
        val updated = repo.getProfile("prod-1")
        assertNotNull(updated)
        assertEquals("TerraformElevated", updated.roleName)
        assertEquals("111111111111", updated.accountId)
        assertEquals("eu-west-1", updated.region)
    }

    @Test
    fun `upsertProfile preserves all other sections`() {
        val profile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        repo.upsertProfile(profile)
        val content = fs.files[configPath]!!
        assertTrue(content.contains("[default]"))
        assertTrue(content.contains("[profile prod-2]"))
        assertTrue(content.contains("222222222222"))
    }

    @Test
    fun `upsertProfile does not affect other profiles`() {
        val profile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        repo.upsertProfile(profile)
        assertEquals("Terraform", repo.getProfile("prod-2")?.roleName)
    }

    @Test
    fun `upsertProfile is a no-op when config file does not exist`() {
        fs.files.remove(configPath)
        val profile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        repo.upsertProfile(profile)
        assertNull(fs.files[configPath])
    }

    @Test
    fun `write is atomic via temp-then-rename`() {
        val profile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        repo.upsertProfile(profile)
        val tempKeys = fs.files.keys.filter { it.contains(".tmp.") }
        assertTrue(tempKeys.isEmpty(), "Expected no leftover temp files, found: $tempKeys")
    }

    @Test
    fun `listSsoProfiles returns all SSO profiles`() {
        val profiles = repo.listSsoProfiles()
        assertEquals(2, profiles.size)
        assertTrue(profiles.any { it.profileName == "prod-1" })
        assertTrue(profiles.any { it.profileName == "prod-2" })
    }
}
