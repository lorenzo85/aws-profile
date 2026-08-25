package com.argol.awsprofile.infrastructure

import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.infrastructure.config.TomlConfigurationRepository
import com.argol.awsprofile.infrastructure.filesystem.NativeFileSystem
import com.argol.awsprofile.infrastructure.filesystem.NativeUserDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TomlConfigurationRepositoryTest {

    private val repo = TomlConfigurationRepository(NativeFileSystem(), NativeUserDirectories())

    @Test
    fun `parses elevated_suffix`() {
        val config = repo.parse("elevated_suffix = \"Elevated\"")
        assertEquals("Elevated", config.elevatedSuffix)
    }

    @Test
    fun `parses unquoted value`() {
        val config = repo.parse("elevated_suffix = Elevated")
        assertEquals("Elevated", config.elevatedSuffix)
    }

    @Test
    fun `throws ConfigurationError when elevated_suffix is missing`() {
        assertFailsWith<ConfigurationError> { repo.parse("# empty") }
    }

    @Test
    fun `ignores comment lines`() {
        val toml = """
            # this is a comment
            elevated_suffix = "Admin"
        """.trimIndent()
        val config = repo.parse(toml)
        assertEquals("Admin", config.elevatedSuffix)
    }

    @Test
    fun `strips inline comment from value`() {
        val config = repo.parse("elevated_suffix = \"Elevated\" # default")
        assertEquals("Elevated", config.elevatedSuffix)
    }
}
