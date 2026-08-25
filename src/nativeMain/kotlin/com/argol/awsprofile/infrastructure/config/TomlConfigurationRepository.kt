package com.argol.awsprofile.infrastructure.config

import com.argol.awsprofile.domain.AppConfig
import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.infrastructure.filesystem.FileSystem
import com.argol.awsprofile.infrastructure.filesystem.Path
import com.argol.awsprofile.infrastructure.filesystem.UserDirectories
import com.argol.awsprofile.ports.ConfigurationRepository

class TomlConfigurationRepository(
    private val fileSystem: FileSystem,
    private val userDirectories: UserDirectories
) : ConfigurationRepository {

    private val configPath: Path
        get() = userDirectories.configDirectory() / "config.toml"

    override fun exists(): Boolean = fileSystem.exists(configPath)

    override fun write(content: String) {
        val dir = userDirectories.configDirectory()
        if (!fileSystem.exists(dir)) fileSystem.createDirectories(dir)
        fileSystem.write(configPath, content)
        fileSystem.setRestrictivePermissions(configPath)
    }

    override fun load(): AppConfig {
        val path = configPath
        if (!fileSystem.exists(path)) {
            throw ConfigurationError(
                "Configuration file not found: ${path.value}\n" +
                "Run 'aws-profile init' to create it."
            )
        }
        return parse(fileSystem.read(path))
    }

    internal fun parse(content: String): AppConfig {
        val values = mutableMapOf<String, String>()

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue
            if (!line.contains("=")) continue
            val eqIdx = line.indexOf('=')
            val key = line.substring(0, eqIdx).trim()
            var value = line.substring(eqIdx + 1).trim()
            val commentIdx = value.indexOf(" #")
            if (commentIdx >= 0) value = value.substring(0, commentIdx).trim()
            if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                value = value.substring(1, value.length - 1)
            }
            values[key] = value
        }

        val suffix = values["elevated_suffix"]
            ?: throw ConfigurationError("Missing elevated_suffix in configuration")

        return AppConfig(elevatedSuffix = suffix)
    }
}
