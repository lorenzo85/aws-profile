package com.argol.awsprofile.infrastructure.filesystem

import com.argol.awsprofile.errors.AwsConfigError
import com.argol.awsprofile.errors.ConfigurationError
import kotlinx.cinterop.*
import platform.posix.*

data class Path(val value: String) {
    operator fun div(segment: String): Path = Path("$value/$segment")
    fun resolve(segment: String): Path = Path("$value/$segment")
    fun parent(): Path? {
        val idx = value.lastIndexOf('/')
        return if (idx > 0) Path(value.substring(0, idx)) else null
    }
    override fun toString(): String = value
}

interface FileSystem {
    fun exists(path: Path): Boolean
    fun read(path: Path): String
    fun readOrNull(path: Path): String?
    fun write(path: Path, content: String)
    fun move(source: Path, target: Path)
    fun createDirectories(path: Path)
    fun setRestrictivePermissions(path: Path)
    fun listFiles(path: Path): List<Path>
}

interface UserDirectories {
    fun home(): Path
    fun configDirectory(): Path
    fun awsDirectory(): Path
}

class NativeUserDirectories : UserDirectories {
    @OptIn(ExperimentalForeignApi::class)
    override fun home(): Path {
        val home = getenv("HOME")?.toKString()
            ?: throw ConfigurationError("HOME environment variable is not set")
        return Path(home)
    }

    override fun configDirectory(): Path = home() / ".config" / "aws-profile"
    override fun awsDirectory(): Path = home() / ".aws"
}

class NativeFileSystem : FileSystem {

    override fun exists(path: Path): Boolean =
        access(path.value, F_OK) == 0

    override fun read(path: Path): String =
        readOrNull(path) ?: throw ConfigurationError("Cannot read file: ${path.value}")

    @OptIn(ExperimentalForeignApi::class)
    override fun readOrNull(path: Path): String? {
        val file = fopen(path.value, "r") ?: return null
        val content = StringBuilder()
        try {
            memScoped {
                val buffer = allocArray<ByteVar>(8192)
                while (true) {
                    val line = fgets(buffer, 8192, file) ?: break
                    content.append(line.toKString())
                }
            }
        } finally {
            fclose(file)
        }
        return content.toString()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun write(path: Path, content: String) {
        val file = fopen(path.value, "w")
            ?: throw AwsConfigError("Cannot write to file: ${path.value}")
        try {
            if (fputs(content, file) == EOF) {
                throw AwsConfigError("Failed to write to file: ${path.value}")
            }
        } finally {
            fclose(file)
        }
    }

    override fun move(source: Path, target: Path) {
        if (rename(source.value, target.value) != 0) {
            throw AwsConfigError("Failed to move ${source.value} to ${target.value}")
        }
    }

    override fun createDirectories(path: Path) {
        createDirectoriesRecursive(path.value)
    }

    // Sets 0600 for files (owner read/write only) — always restrictive for sensitive config
    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    override fun setRestrictivePermissions(path: Path) {
        // 0600 = owner r/w only; convert<mode_t>() handles UShort/UInt difference across platforms
        chmod(path.value, 384u.convert())
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    private fun createDirectoriesRecursive(path: String) {
        if (path.isEmpty()) return
        val parts = path.trimStart('/').split("/")
        val absolute = path.startsWith("/")
        var current = if (absolute) "" else "."
        for (part in parts) {
            current = if (current.isEmpty() || current == ".") {
                if (absolute) "/$part" else part
            } else {
                "$current/$part"
            }
            if (access(current, F_OK) != 0) {
                // 0700: owner read/write/execute only
                mkdir(current, 448u.convert())
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun listFiles(path: Path): List<Path> {
        val dir = opendir(path.value) ?: return emptyList()
        val result = mutableListOf<Path>()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                result.add(path / name)
            }
        } finally {
            closedir(dir)
        }
        return result
    }
}

fun tempFilePath(base: Path): Path =
    Path("${base.value}.tmp.${getpid()}")
