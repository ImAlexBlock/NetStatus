package com.alexblock.netstatus.data.telephony

import com.alexblock.netstatus.data.NetworkDetails

enum class CellTech(val label: String) {
    NR("5G NR"),
    LTE("LTE"),
    GSM("GSM"),
    WCDMA("WCDMA"),
    TDSCDMA("TD-SCDMA"),
    CDMA("CDMA"),
    UNKNOWN("未知"),
}

data class CellDetails(
    val tech: CellTech,
    val registered: Boolean,
    val connectionStatus: String,
    val band: String?,
    val arfcn: Int?,
    val frequencyMhz: Double?,
    val bandwidthMhz: Double?,
    val pci: Int?,
    val tac: Int?,
    val cellId: String?,
    val plmn: String?,
    val operator: String?,
    val ssRsrp: Int?,
    val ssRsrq: Int?,
    val ssSinr: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val level: Int,
    val timingAdvance: Int?,
)

data class WwanSnapshot(
    val simState: String,
    val simOperatorName: String?,
    val networkOperatorName: String?,
    val dataNetworkTypeLabel: String,
    val displayTech: String,
    val serviceStateLabel: String,
    val registered: Boolean,
    val roaming: Boolean,
    val dataEnabled: Boolean?,
    val dataStateLabel: String,
    val dataActivityLabel: String?,
    val signalDbm: Int?,
    val signalLevel: Int,
    val network: NetworkDetails?,
    val cells: List<CellDetails>,
    val lastUpdateMillis: Long,
    val permissionsGranted: Boolean,
    val locationEnabled: Boolean,
    val hasTelephony: Boolean,
    val error: String?,
)
