package com.alexblock.netstatus.data

data class NetworkDetails(
    val ipv4: List<String>,
    val ipv6: List<String>,
    val gateway: String?,
    val dns: List<String>,
    val validated: Boolean,
    val captivePortal: Boolean,
    val internet: Boolean,
    val metered: Boolean,
)

data class ProbeResult(
    val endpoint: String,
    val latencyMs: Long,
    val success: Boolean,
    val atMillis: Long,
)

enum class CheckStatus { PASS, WARN, FAIL, UNKNOWN }

data class DiagnosticCheck(
    val title: String,
    val detail: String,
    val status: CheckStatus,
)
