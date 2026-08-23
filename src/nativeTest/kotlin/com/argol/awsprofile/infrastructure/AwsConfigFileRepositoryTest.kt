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

// ─── In-memory filesystem for isolation ─────────────────────────────────────

class InMemoryFileSystem : FileSystem {
    val files = mutableMapOf<String, String>()
    val directories = mutableSetOf<String>()

    override fun exists(path: Path): Boolean =
        files.containsKey(path.value) || directories.contains(path.value)

    override fun read(path: Path): String =
        files[path.value] ?: error("File not found: ${path.value}")

    override fun readOrNull(path: Path): String? = files[path.value]

    override fun write(path: Path, content: String) {
        files[path.value] = content
    }

    override fun move(source: Path, target: Path) {
        val content = files.remove(source.value) ?: error("Source not found: ${source.value}")
        files[target.value] = content
    }

    override fun createDirectories(path: Path) {
        directories.add(path.value)
    }

    override fun setRestrictivePermissions(path: Path) {}
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
    private lateinit var dirs: FakeUserDirectories
    private lateinit var repo: AwsConfigFileRepository

    @BeforeTest
    fun setup() {
        fs = InMemoryFileSystem()
        dirs = FakeUserDirectories()
        repo = AwsConfigFileRepository(fs, dirs)
    }

    private val sampleProfile = AwsProfile(
        name = "prod-1",
        ssoSession = "company",
        accountId = "111111111111",
        roleName = "Terraform",
        region = "eu-west-1"
    )

    private val elevatedProfile = sampleProfile.copy(roleName = "TerraformElevated")

    @Test
    fun `getProfile returns null when config file does not exist`() {
        assertNull(repo.getProfile("prod-1"))
    }

    @Test
    fun `upsertProfile creates aws config when it does not exist`() {
        repo.upsertProfile(sampleProfile)
        assertTrue(fs.files.containsKey("/home/testuser/.aws/config"))
    }

    @Test
    fun `upsertProfile creates aws directory when it does not exist`() {
        repo.upsertProfile(sampleProfile)
        assertTrue(fs.directories.contains("/home/testuser/.aws"))
    }

    @Test
    fun `getProfile retrieves previously written profile`() {
        repo.upsertProfile(sampleProfile)
        val retrieved = repo.getProfile("prod-1")
        assertNotNull(retrieved)
        assertEquals("prod-1", retrieved.name)
        assertEquals("Terraform", retrieved.roleName)
        assertEquals("111111111111", retrieved.accountId)
    }

    @Test
    fun `upsertProfile replaces existing profile`() {
        repo.upsertProfile(sampleProfile)
        repo.upsertProfile(elevatedProfile)
        val retrieved = repo.getProfile("prod-1")
        assertNotNull(retrieved)
        assertEquals("TerraformElevated", retrieved.roleName)
    }

    @Test
    fun `upsertProfile preserves unrelated profiles`() {
        val existingConfig = """
            [default]
            region = eu-west-1

            [profile other-tool]
            region = us-east-1

            [profile prod-1]
            sso_session = company
            sso_account_id = 111111111111
            sso_role_name = Terraform
            region = eu-west-1
            output = json
        """.trimIndent()
        fs.files["/home/testuser/.aws/config"] = existingConfig

        repo.upsertProfile(elevatedProfile)

        val content = fs.files["/home/testuser/.aws/config"]!!
        assertTrue(content.contains("[default]"))
        assertTrue(content.contains("[profile other-tool]"))
        assertTrue(content.contains("us-east-1"))
        assertTrue(content.contains("TerraformElevated"))
    }

    @Test
    fun `upsertProfile does not corrupt other profile when replacing`() {
        val existingConfig = """
            [profile prod-2]
            sso_session = company
            sso_account_id = 222222222222
            sso_role_name = Terraform
            region = eu-west-1
            output = json

            [profile prod-1]
            sso_session = company
            sso_account_id = 111111111111
            sso_role_name = Terraform
            region = eu-west-1
            output = json
        """.trimIndent()
        fs.files["/home/testuser/.aws/config"] = existingConfig

        repo.upsertProfile(elevatedProfile)

        val prod2 = repo.getProfile("prod-2")
        assertNotNull(prod2)
        assertEquals("222222222222", prod2.accountId)
        assertEquals("Terraform", prod2.roleName)
    }

    @Test
    fun `getProfile returns null for non-existent profile in existing config`() {
        val existingConfig = """
            [profile prod-2]
            sso_session = company
            sso_account_id = 222222222222
            sso_role_name = Terraform
            region = eu-west-1
            output = json
        """.trimIndent()
        fs.files["/home/testuser/.aws/config"] = existingConfig
        assertNull(repo.getProfile("prod-1"))
    }

    @Test
    fun `upsertProfile on empty existing config appends section`() {
        fs.files["/home/testuser/.aws/config"] = ""
        repo.upsertProfile(sampleProfile)
        val retrieved = repo.getProfile("prod-1")
        assertNotNull(retrieved)
    }

    @Test
    fun `write is atomic via temp-then-rename`() {
        // The in-memory FS simulates move: the temp file should not persist after move
        repo.upsertProfile(sampleProfile)
        val tempKeys = fs.files.keys.filter { it.endsWith(".tmp.") || it.contains(".tmp.") }
        assertTrue(tempKeys.isEmpty(), "Expected no leftover temp files, found: $tempKeys")
    }
}
