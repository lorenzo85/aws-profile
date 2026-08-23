package com.argol.awsprofile.infrastructure

import com.argol.awsprofile.domain.AwsProfile
import com.argol.awsprofile.infrastructure.aws.AwsConfigDocument
import com.argol.awsprofile.infrastructure.aws.AwsConfigParser
import com.argol.awsprofile.infrastructure.aws.AwsConfigSectionRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwsConfigParserTest {

    private val sampleConfig = """
        [default]
        region = eu-west-1

        [profile unrelated]
        region = us-east-1

        [profile prod-1]
        sso_session = company
        sso_account_id = 111111111111
        sso_role_name = Terraform
        region = eu-west-1
        output = json
    """.trimIndent()

    @Test
    fun `parse extracts all sections`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        assertEquals(3, doc.sections.size)
        assertEquals("default", doc.sections[0].header)
        assertEquals("profile unrelated", doc.sections[1].header)
        assertEquals("profile prod-1", doc.sections[2].header)
    }

    @Test
    fun `extractProfile returns correct AwsProfile`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val section = doc.sections.first { it.header == "profile prod-1" }
        val profile = AwsConfigParser.extractProfile(section)
        assertNotNull(profile)
        assertEquals("prod-1", profile.name)
        assertEquals("company", profile.ssoSession)
        assertEquals("111111111111", profile.accountId)
        assertEquals("Terraform", profile.roleName)
        assertEquals("eu-west-1", profile.region)
        assertEquals("json", profile.output)
    }

    @Test
    fun `extractProfile returns null for non-profile section`() {
        val section = AwsConfigSectionRaw("default", "region = eu-west-1\n")
        assertNull(AwsConfigParser.extractProfile(section))
    }

    @Test
    fun `upsert replaces existing profile without touching other sections`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val newProfile = AwsProfile(
            name = "prod-1",
            ssoSession = "company",
            accountId = "111111111111",
            roleName = "TerraformElevated",
            region = "eu-west-1"
        )
        val updated = AwsConfigParser.upsert(doc, newProfile)

        assertEquals(3, updated.sections.size)
        // Unrelated sections are unchanged
        assertEquals("default", updated.sections[0].header)
        assertEquals("profile unrelated", updated.sections[1].header)

        // Target section is updated
        val updatedSection = updated.sections[2]
        assertEquals("profile prod-1", updatedSection.header)
        assertTrue(updatedSection.body.contains("TerraformElevated"))
    }

    @Test
    fun `upsert appends new profile if not present`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val newProfile = AwsProfile(
            name = "prod-99",
            ssoSession = "company",
            accountId = "999999999999",
            roleName = "Terraform",
            region = "us-east-1"
        )
        val updated = AwsConfigParser.upsert(doc, newProfile)
        assertEquals(4, updated.sections.size)
        assertEquals("profile prod-99", updated.sections[3].header)
    }

    @Test
    fun `serialize round-trips correctly`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val serialized = AwsConfigParser.serialize(doc)
        val reparsed = AwsConfigParser.parse(serialized)
        assertEquals(doc.sections.size, reparsed.sections.size)
        assertEquals(doc.sections.map { it.header }, reparsed.sections.map { it.header })
    }

    @Test
    fun `parse handles empty config`() {
        val doc = AwsConfigParser.parse("")
        assertTrue(doc.sections.isEmpty())
    }

    @Test
    fun `upsert on empty document appends section`() {
        val doc = AwsConfigDocument(emptyList())
        val profile = AwsProfile(
            name = "prod-1",
            ssoSession = "company",
            accountId = "111111111111",
            roleName = "Terraform",
            region = "eu-west-1"
        )
        val updated = AwsConfigParser.upsert(doc, profile)
        assertEquals(1, updated.sections.size)
        assertEquals("profile prod-1", updated.sections[0].header)
    }

    @Test
    fun `profile at beginning of config is replaced correctly`() {
        val config = """
            [profile prod-1]
            sso_session = company
            sso_account_id = 111111111111
            sso_role_name = Terraform
            region = eu-west-1
            output = json

            [default]
            region = eu-west-1
        """.trimIndent()
        val doc = AwsConfigParser.parse(config)
        val newProfile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        val updated = AwsConfigParser.upsert(doc, newProfile)

        assertEquals("profile prod-1", updated.sections[0].header)
        assertTrue(updated.sections[0].body.contains("TerraformElevated"))
        assertEquals("default", updated.sections[1].header)
        assertEquals(2, updated.sections.size)
    }

    @Test
    fun `profile at end of config is replaced correctly`() {
        val config = """
            [default]
            region = eu-west-1

            [profile prod-1]
            sso_session = company
            sso_account_id = 111111111111
            sso_role_name = Terraform
            region = eu-west-1
            output = json
        """.trimIndent()
        val doc = AwsConfigParser.parse(config)
        val newProfile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        val updated = AwsConfigParser.upsert(doc, newProfile)

        assertEquals(2, updated.sections.size)
        assertEquals("profile prod-1", updated.sections[1].header)
        assertTrue(updated.sections[1].body.contains("TerraformElevated"))
    }

    @Test
    fun `upsert does not modify unrelated profile`() {
        val config = """
            [profile other-tool]
            region = us-east-1
            aws_access_key_id = AKIAIOSFODNN7EXAMPLE

            [profile prod-1]
            sso_session = company
            sso_account_id = 111111111111
            sso_role_name = Terraform
            region = eu-west-1
            output = json
        """.trimIndent()
        val doc = AwsConfigParser.parse(config)
        val newProfile = AwsProfile("prod-1", "company", "111111111111", "TerraformElevated", "eu-west-1")
        val updated = AwsConfigParser.upsert(doc, newProfile)

        val otherSection = updated.sections.first { it.header == "profile other-tool" }
        assertTrue(otherSection.body.contains("us-east-1"))
        assertTrue(otherSection.body.contains("AKIAIOSFODNN7EXAMPLE"))
    }

    @Test
    fun `config with only default section gets profile appended`() {
        val config = """
            [default]
            region = eu-west-1
        """.trimIndent()
        val doc = AwsConfigParser.parse(config)
        val profile = AwsProfile("prod-1", "company", "111111111111", "Terraform", "eu-west-1")
        val updated = AwsConfigParser.upsert(doc, profile)
        assertEquals(2, updated.sections.size)
    }
}
