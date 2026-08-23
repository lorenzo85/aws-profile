package com.argol.awsprofile.ports

import com.argol.awsprofile.domain.AppConfig

interface ConfigurationRepository {
    fun load(): AppConfig
}
