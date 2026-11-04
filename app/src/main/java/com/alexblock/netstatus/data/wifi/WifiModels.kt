package com.alexblock.netstatus.data.wifi

import com.alexblock.netstatus.data.NetworkDetails
import com.alexblock.netstatus.util.WifiBand
import com.alexblock.netstatus.util.WifiSecurity
import com.alexblock.netstatus.util.WifiStandard

data class WifiConnection(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val standard: WifiStandard,
    val security: WifiSecurity,
    val txMbps: Int?,
    val rxMbps: Int?,
    val maxTxMbps: Int?,
    val maxRxMbps: Int?,
    val channelWidth: Int?,
    val supplicantState: String?,
    val hiddenSsid: Boolean,
)

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: WifiBand,
    val width: Int?,
    val standard: WifiStandard,
    val security: String,
    val connected: Boolean,
)

data class WifiSnapshot(
    val wifiEnabled: Boolean,
    val connection: WifiConnection?,
    val network: NetworkDetails?,
    val accessPoints: List<WifiAp>,
    val lastScanAtMillis: Long?,
    val scanning: Boolean,
    val scanThrottled: Boolean,
    val locationEnabled: Boolean,
    val permissionsGranted: Boolean,
    val error: String?,
)
