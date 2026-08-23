package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.domain.AwsProfile

// Raw representation preserving file content for unrelated sections
data class AwsConfigDocument(
    val sections: List<AwsConfigSectionRaw>
)

data class AwsConfigSectionRaw(
    val header: String,      // e.g. "profile prod-1" or "default"
    val body: String         // raw text of entries (after the header line, before next header)
)

object AwsConfigParser {

    fun parse(text: String): AwsConfigDocument {
        val lines = text.lines()
        val sections = mutableListOf<AwsConfigSectionRaw>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (isSectionHeader(trimmed)) {
                val header = trimmed.substring(1, trimmed.length - 1).trim()
                i++
                val bodyLines = mutableListOf<String>()
                while (i < lines.size && !isSectionHeader(lines[i].trim())) {
                    bodyLines.add(lines[i])
                    i++
                }
                // Trim trailing blank lines from body but keep one trailing newline
                val body = bodyLines.joinToString("\n").trimEnd() + "\n"
                sections.add(AwsConfigSectionRaw(header, body))
            } else {
                // Lines before any section header are discarded (usually empty)
                i++
            }
        }
        return AwsConfigDocument(sections)
    }

    fun serialize(doc: AwsConfigDocument): String {
        if (doc.sections.isEmpty()) return ""
        return buildString {
            doc.sections.forEachIndexed { index, section ->
                if (index > 0) append("\n")
                appendLine("[${section.header}]")
                append(section.body)
            }
        }
    }

    fun upsert(doc: AwsConfigDocument, profile: AwsProfile): AwsConfigDocument {
        val targetHeader = "profile ${profile.name}"
        val newBody = profileBody(profile)
        val newSection = AwsConfigSectionRaw(targetHeader, newBody)

        val existingIndex = doc.sections.indexOfFirst { it.header == targetHeader }
        val newSections = if (existingIndex >= 0) {
            doc.sections.toMutableList().also { it[existingIndex] = newSection }
        } else {
            doc.sections + newSection
        }
        return doc.copy(sections = newSections)
    }

    fun extractProfile(section: AwsConfigSectionRaw): AwsProfile? {
        if (!section.header.startsWith("profile ")) return null
        val name = section.header.removePrefix("profile ").trim()
        val entries = parseEntries(section.body)
        val ssoSession = entries["sso_session"] ?: return null
        val accountId = entries["sso_account_id"] ?: return null
        val roleName = entries["sso_role_name"] ?: return null
        val region = entries["region"] ?: return null
        val output = entries["output"] ?: "json"
        return AwsProfile(
            name = name,
            ssoSession = ssoSession,
            accountId = accountId,
            roleName = roleName,
            region = region,
            output = output
        )
    }

    private fun profileBody(profile: AwsProfile): String = buildString {
        appendLine("sso_session = ${profile.ssoSession}")
        appendLine("sso_account_id = ${profile.accountId}")
        appendLine("sso_role_name = ${profile.roleName}")
        appendLine("region = ${profile.region}")
        appendLine("output = ${profile.output}")
    }

    private fun parseEntries(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (rawLine in body.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            val eqIdx = line.indexOf('=')
            if (eqIdx < 0) continue
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()
            result[key] = value
        }
        return result
    }

    private fun isSectionHeader(trimmed: String): Boolean =
        trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length >= 3
}
