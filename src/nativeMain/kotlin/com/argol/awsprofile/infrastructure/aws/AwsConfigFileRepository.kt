package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.domain.AwsProfile
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
        val doc = AwsConfigParser.parse(text)
        return doc.sections
            .firstOrNull { it.header == "profile $name" }
            ?.let { AwsConfigParser.extractProfile(it) }
    }

    override fun upsertProfile(profile: AwsProfile) {
        ensureAwsDirectoryExists()

        val existing = fileSystem.readOrNull(configPath)
        val doc = if (existing != null) {
            AwsConfigParser.parse(existing)
        } else {
            AwsConfigDocument(emptyList())
        }

        val updated = AwsConfigParser.upsert(doc, profile)
        val content = AwsConfigParser.serialize(updated)

        writeAtomically(configPath, content)
    }

    private fun writeAtomically(target: Path, content: String) {
        val temp = tempFilePath(target)
        try {
            fileSystem.write(temp, content)
            // Always write config with restrictive permissions (0600 = owner r/w only)
            fileSystem.setRestrictivePermissions(temp)
            fileSystem.move(temp, target)
        } catch (e: Exception) {
            throw AwsConfigError("Failed to update AWS config: ${e.message}")
        }
    }

    private fun ensureAwsDirectoryExists() {
        val dir = userDirectories.awsDirectory()
        if (!fileSystem.exists(dir)) {
            fileSystem.createDirectories(dir)
        }
    }
}
