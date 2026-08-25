package com.argol.awsprofile.infrastructure

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
    fun `parse handles empty config`() {
        assertTrue(AwsConfigParser.parse("").sections.isEmpty())
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
    }

    @Test
    fun `extractProfile works without sso_session`() {
        val section = AwsConfigSectionRaw(
            "profile legacy",
            "sso_account_id = 111111111111\nsso_role_name = Terraform\nregion = eu-west-1\n"
        )
        val profile = AwsConfigParser.extractProfile(section)
        assertNotNull(profile)
        assertEquals("", profile.ssoSession)
        assertEquals("Terraform", profile.roleName)
    }

    @Test
    fun `extractProfile returns null for non-profile section`() {
        assertNull(AwsConfigParser.extractProfile(AwsConfigSectionRaw("default", "region = eu-west-1\n")))
    }

    @Test
    fun `extractProfile falls back to sso_region when region is absent`() {
        val section = AwsConfigSectionRaw(
            "profile legacy",
            "sso_account_id = 111111111111\nsso_role_name = Terraform\nsso_region = eu-west-1\n"
        )
        val profile = AwsConfigParser.extractProfile(section)
        assertNotNull(profile)
        assertEquals("eu-west-1", profile.region)
    }

    @Test
    fun `extractSsoProfile falls back to sso_region when region is absent`() {
        val section = AwsConfigSectionRaw(
            "profile legacy",
            "sso_account_id = 111111111111\nsso_role_name = Terraform\nsso_region = eu-west-1\n"
        )
        val profile = AwsConfigParser.extractSsoProfile(section)
        assertNotNull(profile)
        assertEquals("eu-west-1", profile.region)
    }

    @Test
    fun `updateRoleName changes only sso_role_name`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val updated = AwsConfigParser.updateRoleName(doc, "prod-1", "TerraformElevated")
        val section = updated.sections.first { it.header == "profile prod-1" }
        assertTrue(section.body.contains("sso_role_name = TerraformElevated"))
        assertTrue(section.body.contains("sso_account_id = 111111111111"))
        assertTrue(section.body.contains("sso_session = company"))
        assertTrue(section.body.contains("region = eu-west-1"))
    }

    @Test
    fun `updateRoleName does not touch other sections`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val updated = AwsConfigParser.updateRoleName(doc, "prod-1", "TerraformElevated")
        assertEquals(3, updated.sections.size)
        assertEquals("default", updated.sections[0].header)
        assertEquals("profile unrelated", updated.sections[1].header)
        val unrelated = updated.sections[1]
        assertTrue(unrelated.body.contains("us-east-1"))
    }

    @Test
    fun `updateRoleName is a no-op for unknown profile`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val updated = AwsConfigParser.updateRoleName(doc, "ghost", "TerraformElevated")
        assertEquals(doc, updated)
    }

    @Test
    fun `serialize round-trips correctly`() {
        val doc = AwsConfigParser.parse(sampleConfig)
        val reparsed = AwsConfigParser.parse(AwsConfigParser.serialize(doc))
        assertEquals(doc.sections.map { it.header }, reparsed.sections.map { it.header })
    }
}
