package com.hakdaar.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockedTargetsConfig(
    val version: Int = 1,
    val blockedPackages: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
    val englishSensitiveTerms: List<String> = emptyList(),
    val hindiSensitiveTerms: List<String> = emptyList()
)
