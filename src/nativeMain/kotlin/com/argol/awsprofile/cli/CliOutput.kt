package com.argol.awsprofile.cli

import platform.posix.fputs
import platform.posix.stderr

interface CliOutput {
    fun info(message: String)
    fun success(message: String)
    fun error(message: String)
    fun println(message: String = "")
}

class ConsoleOutput : CliOutput {
    override fun info(message: String) = kotlin.io.println(message)
    override fun success(message: String) = kotlin.io.println(message)
    override fun error(message: String) { fputs(message + "\n", stderr) }
    override fun println(message: String) = kotlin.io.println(message)
}
