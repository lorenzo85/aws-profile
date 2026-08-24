package com.argol.awsprofile.infrastructure.config

import com.argol.awsprofile.domain.Account
import com.argol.awsprofile.domain.AppConfig
import com.argol.awsprofile.domain.PermissionSetName
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
                "Create it at ${path.value} — see README for the format."
            )
        }
        val content = fileSystem.read(path)
        return parse(content)
    }

    internal fun parse(content: String): AppConfig {
        val sections = parseToml(content)

        val ssoSection = sections["sso"]
            ?: throw ConfigurationError("Missing [sso] section in configuration")
        val ssoSession = ssoSection["session"]
            ?: throw ConfigurationError("Missing sso.session in configuration")

        val permSection = sections["permission_sets"]
            ?: throw ConfigurationError("Missing [permission_sets] section in configuration")
        val standing = permSection["standing"]
            ?: throw ConfigurationError("Missing permission_sets.standing in configuration")
        val elevated = permSection["elevated"]   // optional — null means no global elevated access

        val accounts = sections
            .filterKeys { it.startsWith("accounts.") }
            .mapNotNull { (key, values) ->
                val alias = key.removePrefix("accounts.")
                if (alias.isBlank()) return@mapNotNull null
                val accountId = values["account_id"]
                    ?: throw ConfigurationError("Missing account_id for [$key]")
                val region = values["region"]
                    ?: throw ConfigurationError("Missing region for [$key]")
                val accountStanding = values["standing"]?.let { PermissionSetName(it) }
                val accountElevated = values["elevated"]?.let { PermissionSetName(it) }
                alias to Account(
                    alias = alias,
                    accountId = accountId,
                    region = region,
                    standingPermissionSet = accountStanding,
                    elevatedPermissionSet = accountElevated
                )
            }
            .toMap()

        if (accounts.isEmpty()) {
            throw ConfigurationError("No accounts defined in configuration")
        }

        return AppConfig(
            ssoSession = ssoSession,
            standingPermissionSet = PermissionSetName(standing),
            elevatedPermissionSet = elevated?.let { PermissionSetName(it) },
            accounts = accounts
        )
    }

    // Minimal TOML parser for the config subset we use:
    //   [section]
    //   [section.subsection]
    //   key = "value"
    //   key = value   (unquoted)
    //   # comments
    private fun parseToml(content: String): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection = ""

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> continue

                line.startsWith("[") && line.endsWith("]") -> {
                    currentSection = line.substring(1, line.length - 1).trim()
                    result.getOrPut(currentSection) { mutableMapOf() }
                }

                line.contains("=") -> {
                    val eqIdx = line.indexOf('=')
                    val key = line.substring(0, eqIdx).trim()
                    var value = line.substring(eqIdx + 1).trim()
                    // Strip surrounding double quotes if present
                    if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                        value = value.substring(1, value.length - 1)
                    }
                    result.getOrPut(currentSection) { mutableMapOf() }[key] = value
                }
            }
        }
        return result
    }
}
