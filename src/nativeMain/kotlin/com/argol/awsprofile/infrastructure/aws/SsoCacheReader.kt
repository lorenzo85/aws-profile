package com.argol.awsprofile.infrastructure.aws

import com.argol.awsprofile.infrastructure.filesystem.FileSystem
import com.argol.awsprofile.infrastructure.filesystem.UserDirectories

data class SsoToken(
    val accessToken: String,
    val region: String,
    val startUrl: String
)

class SsoCacheReader(
    private val fileSystem: FileSystem,
    private val userDirectories: UserDirectories
) {
    fun findValidToken(): SsoToken? {
        val cacheDir = userDirectories.awsDirectory() / "sso" / "cache"
        if (!fileSystem.exists(cacheDir)) return null

        return fileSystem.listFiles(cacheDir)
            .filter { it.value.endsWith(".json") }
            .mapNotNull { path ->
                val content = fileSystem.readOrNull(path) ?: return@mapNotNull null
                parseToken(content)
            }
            .firstOrNull()
    }

    private fun parseToken(json: String): SsoToken? {
        val accessToken = extractJsonString(json, "accessToken") ?: return null
        val region = extractJsonString(json, "region") ?: return null
        val startUrl = extractJsonString(json, "startUrl") ?: return null
        return SsoToken(accessToken = accessToken, region = region, startUrl = startUrl)
    }

    private fun extractJsonString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
}
