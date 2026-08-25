package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.errors.AwsConfigError
import com.argol.awsprofile.infrastructure.filesystem.FileSystem
import com.argol.awsprofile.infrastructure.filesystem.Path
import com.argol.awsprofile.infrastructure.filesystem.UserDirectories
import com.argol.awsprofile.infrastructure.filesystem.tempFilePath
import com.argol.awsprofile.ports.AwsConfigRepository

class AwsConfigFileRepository(
    private val fileSystem: FileSystem,
    private val userDirectories: UserDirectories
) : AwsConfigRepository {

    private val configPath: Path
        get() = userDirectories.awsDirectory() / "config"

    override fun getProfile(name: String): AwsProfile? {
        val text = fileSystem.readOrNull(configPath) ?: return null
        return AwsConfigParser.parse(text).sections
            .firstOrNull { it.header == "profile $name" }
            ?.let { AwsConfigParser.extractProfile(it) }
    }

    override fun upsertProfile(profile: AwsProfile) = upsertProfiles(listOf(profile))

    override fun upsertProfiles(profiles: List<AwsProfile>) {
        val text = fileSystem.readOrNull(configPath) ?: return
        var doc = AwsConfigParser.parse(text)
        profiles.forEach { doc = AwsConfigParser.updateRoleName(doc, it.name, it.roleName) }
        writeAtomically(configPath, AwsConfigParser.serialize(doc))
    }

    override fun listSsoProfiles(): List<DiscoveredSsoProfile> {
        val text = fileSystem.readOrNull(configPath) ?: return emptyList()
        return AwsConfigParser.parse(text).sections.mapNotNull { AwsConfigParser.extractSsoProfile(it) }
    }

    private fun writeAtomically(target: Path, content: String) {
        val temp = tempFilePath(target)
        try {
            fileSystem.write(temp, content)
            fileSystem.setRestrictivePermissions(temp)
            fileSystem.move(temp, target)
        } catch (e: Exception) {
            throw AwsConfigError("Failed to update AWS config: ${e.message}")
        }
    }
}
