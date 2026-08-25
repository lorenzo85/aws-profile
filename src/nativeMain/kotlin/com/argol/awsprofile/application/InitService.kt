package com.argol.awsprofile.application

import com.argol.awsprofile.errors.ConfigurationError
import com.argol.awsprofile.ports.ConfigurationRepository

class InitService(private val configurationRepository: ConfigurationRepository) {

    fun init() {
        if (configurationRepository.exists()) {
            throw ConfigurationError(
                "Config already exists at ~/.config/aws-profile/config.toml\n" +
                "Delete it first if you want to recreate it."
            )
        }
        configurationRepository.write(
            "# Suffix appended to the standing role name to get the elevated role.\n" +
            "# Example: Terraform -> TerraformElevated\n" +
            "elevated_suffix = \"Elevated\"\n"
        )
    }
}
