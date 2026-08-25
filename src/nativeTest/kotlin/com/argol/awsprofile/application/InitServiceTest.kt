package com.argol.awsprofile.application

import com.argol.awsprofile.domain.AppConfig
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.ConfigurationRepository
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FakeConfigurationRepositoryForInit(
    private val fileExists: Boolean = false
) : ConfigurationRepository {
    var written: String? = null
    override fun exists(): Boolean = fileExists
    override fun write(content: String) { written = content }
    override fun load(): AppConfig = AppConfig(elevatedSuffix = "Elevated")
}

class InitServiceTest {

    @Test
    fun `throws ConfigurationError when config already exists`() {
        assertFailsWith<ConfigurationError> {
            InitService(FakeConfigurationRepositoryForInit(fileExists = true)).init()
        }
    }

    @Test
    fun `writes config when file does not exist`() {
        val repo = FakeConfigurationRepositoryForInit()
        InitService(repo).init()
        assertNotNull(repo.written)
    }

    @Test
    fun `written config contains elevated_suffix`() {
        val repo = FakeConfigurationRepositoryForInit()
        InitService(repo).init()
        assertContains(repo.written!!, "elevated_suffix")
    }

    @Test
    fun `written config default suffix is Elevated`() {
        val repo = FakeConfigurationRepositoryForInit()
        InitService(repo).init()
        assertContains(repo.written!!, "\"Elevated\"")
    }
}
