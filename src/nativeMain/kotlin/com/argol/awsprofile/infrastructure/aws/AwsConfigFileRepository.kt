package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.DiscoveredSsoProfile
import com.argol.awsprofile.domain.SsoSession
import com.argol.awsprofile.errors.AwsConfigError
import com.argol.awsprofile.infrastructure.filesystem.FileSystem
import com.argol.awsprofile.infrastructure.filesystem.Path
import com.argol.awsprofile.infrastructure.filesystem.UserDirectories
import com.argol.awsprofile.infrastructure.filesystem.tempFilePath
import com.argol.awsprofile.ports.AwsConfigRepository

private const val MARKER_BEGIN = "#### DO NOT TOUCH - managed by aws-profile ####"
private const val MARKER_END   = "#### END aws-profile ####"

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
        upsertProfiles(listOf(profile))
    }

    override fun upsertProfiles(profiles: List<AwsProfile>) {
        ensureAwsDirectoryExists()

        val existing = fileSystem.readOrNull(configPath)
        var doc = if (existing != null) AwsConfigParser.parse(existing)
                  else AwsConfigDocument(emptyList())

        profiles.forEach { doc = AwsConfigParser.upsert(doc, it) }

        val managedHeaders = profiles.map { "profile ${it.name}" }.toSet()
        writeAtomically(configPath, serializeWithMarkers(doc, managedHeaders))
    }

    private fun serializeWithMarkers(doc: AwsConfigDocument, managedHeaders: Set<String>): String {
        val unmanaged = doc.sections.filter { it.header !in managedHeaders }
        val managed   = doc.sections.filter { it.header in managedHeaders }

        return buildString {
            unmanaged.forEachIndexed { i, section ->
                if (i > 0) append("\n")
                appendLine("[${section.header}]")
                append(section.body)
            }

            if (managed.isNotEmpty()) {
                if (unmanaged.isNotEmpty()) append("\n")
                appendLine(MARKER_BEGIN)
                managed.forEachIndexed { i, section ->
                    if (i > 0) append("\n")
                    appendLine("[${section.header}]")
                    append(section.body)
                }
                append(MARKER_END).append("\n")
            }
        }
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

    override fun listSsoProfiles(): List<DiscoveredSsoProfile> {
        val text = fileSystem.readOrNull(configPath) ?: return emptyList()
        return AwsConfigParser.parse(text).sections.mapNotNull { AwsConfigParser.extractSsoProfile(it) }
    }

    override fun findSsoSessions(): List<SsoSession> {
        val text = fileSystem.readOrNull(configPath) ?: return emptyList()
        return AwsConfigParser.parse(text).sections.mapNotNull { AwsConfigParser.extractSsoSession(it) }
    }

    private fun ensureAwsDirectoryExists() {
        val dir = userDirectories.awsDirectory()
        if (!fileSystem.exists(dir)) fileSystem.createDirectories(dir)
    }
}
