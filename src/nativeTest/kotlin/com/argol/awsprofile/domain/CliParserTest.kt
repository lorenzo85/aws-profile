package com.argol.awsprofile.domain

import com.argol.awsprofile.cli.*
import com.argol.awsprofile.domain.AccessLevel
import com.argol.awsprofile.errors.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CliParserTest {

    private val parser = com.argol.awsprofile.cli.CliParser()

    @Test
    fun `bare account name maps to STANDING`() {
        val cmd = parser.parse(arrayOf("prod-1"))
        assertIs<SwitchCommand>(cmd)
        assertEquals("prod-1", cmd.selection.accountAlias)
        assertEquals(AccessLevel.STANDING, cmd.selection.accessLevel)
    }

    @Test
    fun `account name with plus maps to ELEVATED`() {
        val cmd = parser.parse(arrayOf("prod-1+"))
        assertIs<SwitchCommand>(cmd)
        assertEquals("prod-1", cmd.selection.accountAlias)
        assertEquals(AccessLevel.ELEVATED, cmd.selection.accessLevel)
    }

    @Test
    fun `double plus is rejected`() {
        assertFailsWith<ValidationError> { parser.parse(arrayOf("prod-1++")) }
    }

    @Test
    fun `bare plus is rejected`() {
        assertFailsWith<ValidationError> { parser.parse(arrayOf("+")) }
    }

    @Test
    fun `leading plus is rejected`() {
        assertFailsWith<ValidationError> { parser.parse(arrayOf("+prod-1")) }
    }

    @Test
    fun `empty args returns HelpCommand`() {
        assertIs<HelpCommand>(parser.parse(emptyArray()))
    }

    @Test
    fun `--help returns HelpCommand`() {
        assertIs<HelpCommand>(parser.parse(arrayOf("--help")))
    }

    @Test
    fun `-h returns HelpCommand`() {
        assertIs<HelpCommand>(parser.parse(arrayOf("-h")))
    }

    @Test
    fun `help returns HelpCommand`() {
        assertIs<HelpCommand>(parser.parse(arrayOf("help")))
    }

    @Test
    fun `version returns VersionCommand`() {
        assertIs<VersionCommand>(parser.parse(arrayOf("version")))
    }

    @Test
    fun `--version returns VersionCommand`() {
        assertIs<VersionCommand>(parser.parse(arrayOf("--version")))
    }

    @Test
    fun `list returns ListCommand`() {
        assertIs<ListCommand>(parser.parse(arrayOf("list")))
    }

    @Test
    fun `list --verbose returns ListVerboseCommand`() {
        assertIs<ListVerboseCommand>(parser.parse(arrayOf("list", "--verbose")))
    }

    @Test
    fun `current with no args returns CurrentCommand with null profile`() {
        val cmd = parser.parse(arrayOf("current"))
        assertIs<CurrentCommand>(cmd)
        assertEquals(null, cmd.profileName)
    }

    @Test
    fun `current with profile name returns CurrentCommand with profile`() {
        val cmd = parser.parse(arrayOf("current", "prod-1"))
        assertIs<CurrentCommand>(cmd)
        assertEquals("prod-1", cmd.profileName)
    }

    @Test
    fun `login with profile name returns LoginCommand`() {
        val cmd = parser.parse(arrayOf("login", "prod-1"))
        assertIs<LoginCommand>(cmd)
        assertEquals("prod-1", cmd.profileName)
    }

    @Test
    fun `login without profile name throws ValidationError`() {
        assertFailsWith<ValidationError> { parser.parse(arrayOf("login")) }
    }

    @Test
    fun `account alias with hyphens and numbers is valid`() {
        val cmd = parser.parse(arrayOf("us-east-prod-42"))
        assertIs<SwitchCommand>(cmd)
        assertEquals("us-east-prod-42", cmd.selection.accountAlias)
        assertEquals(AccessLevel.STANDING, cmd.selection.accessLevel)
    }

    @Test
    fun `elevated alias with hyphens is valid`() {
        val cmd = parser.parse(arrayOf("us-east-prod-42+"))
        assertIs<SwitchCommand>(cmd)
        assertEquals("us-east-prod-42", cmd.selection.accountAlias)
        assertEquals(AccessLevel.ELEVATED, cmd.selection.accessLevel)
    }
}
