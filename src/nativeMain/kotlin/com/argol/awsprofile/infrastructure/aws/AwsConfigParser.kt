package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.domain.DiscoveredSsoProfile

data class AwsConfigDocument(val sections: List<AwsConfigSectionRaw>)

data class AwsConfigSectionRaw(
    val header: String,   // e.g. "profile prod-1"
    val body: String      // raw text of entries, trailing newline included
)

object AwsConfigParser {

    fun parse(text: String): AwsConfigDocument {
        val lines = text.lines()
        val sections = mutableListOf<AwsConfigSectionRaw>()
        var i = 0

        while (i < lines.size) {
            val trimmed = lines[i].trim()
            if (isSectionHeader(trimmed)) {
                val header = trimmed.substring(1, trimmed.length - 1).trim()
                i++
                val bodyLines = mutableListOf<String>()
                while (i < lines.size && !isSectionHeader(lines[i].trim())) {
                    bodyLines.add(lines[i])
                    i++
                }
                val body = bodyLines.joinToString("\n").trimEnd() + "\n"
                sections.add(AwsConfigSectionRaw(header, body))
            } else {
                i++
            }
        }
        return AwsConfigDocument(sections)
    }

    fun serialize(doc: AwsConfigDocument): String = buildString {
        doc.sections.forEachIndexed { index, section ->
            if (index > 0) append("\n")
            appendLine("[${section.header}]")
            append(section.body)
        }
    }

    // Only updates sso_role_name in the existing section body — preserves everything else.
    fun updateRoleName(doc: AwsConfigDocument, profileName: String, roleName: String): AwsConfigDocument {
        val targetHeader = "profile $profileName"
        return doc.copy(sections = doc.sections.map { section ->
            if (section.header != targetHeader) return@map section
            val newBody = section.body.lines().joinToString("\n") { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("sso_role_name") && trimmed.contains("=")) {
                    val indent = line.takeWhile { it == ' ' || it == '\t' }
                    "${indent}sso_role_name = $roleName"
                } else {
                    line
                }
            }.trimEnd() + "\n"
            section.copy(body = newBody)
        })
    }

    fun extractSsoProfile(section: AwsConfigSectionRaw): DiscoveredSsoProfile? {
        if (!section.header.startsWith("profile ")) return null
        val name = section.header.removePrefix("profile ").trim()
        val entries = parseEntries(section.body)
        val accountId = entries["sso_account_id"] ?: return null
        val roleName = entries["sso_role_name"] ?: return null
        val region = entries["region"] ?: return null
        return DiscoveredSsoProfile(
            profileName = name,
            ssoSession = entries["sso_session"],
            accountId = accountId,
            roleName = roleName,
            region = region
        )
    }

    fun extractProfile(section: AwsConfigSectionRaw): AwsProfile? {
        if (!section.header.startsWith("profile ")) return null
        val name = section.header.removePrefix("profile ").trim()
        val entries = parseEntries(section.body)
        val accountId = entries["sso_account_id"] ?: return null
        val roleName = entries["sso_role_name"] ?: return null
        val region = entries["region"] ?: return null
        return AwsProfile(
            name = name,
            ssoSession = entries["sso_session"] ?: "",
            accountId = accountId,
            roleName = roleName,
            region = region,
            output = entries["output"] ?: "json"
        )
    }

    fun parseEntries(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (rawLine in body.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            val eqIdx = line.indexOf('=')
            if (eqIdx < 0) continue
            result[line.substring(0, eqIdx).trim()] = line.substring(eqIdx + 1).trim()
        }
        return result
    }

    private fun isSectionHeader(trimmed: String): Boolean =
        trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length >= 3
}
