package com.argol.awsprofile.ports

import com.argol.awsprofile.domain.SsoAccount

interface SsoDiscovery {
    fun discover(): List<SsoAccount>
}
