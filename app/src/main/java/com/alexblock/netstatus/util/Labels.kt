package com.alexblock.netstatus.util

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.telephony.TelephonyManager

enum class WifiStandard { UNKNOWN, LEGACY, N, AC, AX, AD, BE }

enum class WifiSecurity {
    UNKNOWN, OPEN, WEP, PSK, SAE, EAP, EAP_WPA3, EAP_WPA3_192, OWE,
    WAPI_PSK, WAPI_CERT, PASSPOINT_R1R2, PASSPOINT_R3, DPP,
}

fun standardLabel(standard: WifiStandard): String = when (standard) {
    WifiStandard.UNKNOWN -> "未知"
    WifiStandard.LEGACY -> "802.11a/b/g"
    WifiStandard.N -> "Wi-Fi 4 (802.11n)"
    WifiStandard.AC -> "Wi-Fi 5 (802.11ac)"
    WifiStandard.AX -> "Wi-Fi 6/6E (802.11ax)"
    WifiStandard.AD -> "802.11ad"
    WifiStandard.BE -> "Wi-Fi 7 (802.11be)"
}

fun securityLabel(security: WifiSecurity): String = when (security) {
    WifiSecurity.UNKNOWN -> "未知"
    WifiSecurity.OPEN -> "开放"
    WifiSecurity.WEP -> "WEP"
    WifiSecurity.PSK -> "WPA2-PSK"
    WifiSecurity.SAE -> "WPA3-SAE"
    WifiSecurity.EAP -> "WPA2-企业"
    WifiSecurity.EAP_WPA3 -> "WPA3-企业"
    WifiSecurity.EAP_WPA3_192 -> "WPA3-企业 192 位"
    WifiSecurity.OWE -> "OWE"
    WifiSecurity.WAPI_PSK -> "WAPI-PSK"
    WifiSecurity.WAPI_CERT -> "WAPI-证书"
    WifiSecurity.PASSPOINT_R1R2 -> "Passpoint R1/R2"
    WifiSecurity.PASSPOINT_R3 -> "Passpoint R3"
    WifiSecurity.DPP -> "DPP"
}

fun rssiQuality(rssi: Int): String = when {
    rssi >= -50 -> "极佳"
    rssi >= -60 -> "良好"
    rssi >= -70 -> "一般"
    rssi >= -80 -> "较差"
    else -> "很差"
}

fun rssiLevel(rssi: Int): Int = when {
    rssi >= -50 -> 4
    rssi >= -60 -> 3
    rssi >= -70 -> 2
    rssi >= -80 -> 1
    else -> 0
}

fun levelLabel(level: Int): String = when (level) {
    0 -> "无信号"
    1 -> "较弱"
    2 -> "一般"
    3 -> "良好"
    else -> "极佳"
}

fun networkTypeLabel(type: Int): String = when (type) {
    TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
    TelephonyManager.NETWORK_TYPE_HSPAP,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_UMTS,
    -> "3G WCDMA"
    TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_GSM,
    -> "2G GSM"
    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G TD-SCDMA"
    TelephonyManager.NETWORK_TYPE_CDMA,
    TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_EHRPD,
    -> "3G CDMA"
    TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi 通话"
    else -> "未知"
}

fun wifiStandardOf(value: Int): WifiStandard = when (value) {
    ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
    ScanResult.WIFI_STANDARD_11N -> WifiStandard.N
    ScanResult.WIFI_STANDARD_11AC -> WifiStandard.AC
    ScanResult.WIFI_STANDARD_11AX -> WifiStandard.AX
    ScanResult.WIFI_STANDARD_11AD -> WifiStandard.AD
    ScanResult.WIFI_STANDARD_11BE -> WifiStandard.BE
    else -> WifiStandard.UNKNOWN
}

fun wifiSecurityOf(value: Int): WifiSecurity = when (value) {
    WifiInfo.SECURITY_TYPE_OPEN -> WifiSecurity.OPEN
    WifiInfo.SECURITY_TYPE_WEP -> WifiSecurity.WEP
    WifiInfo.SECURITY_TYPE_PSK -> WifiSecurity.PSK
    WifiInfo.SECURITY_TYPE_SAE -> WifiSecurity.SAE
    WifiInfo.SECURITY_TYPE_EAP -> WifiSecurity.EAP
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> WifiSecurity.EAP_WPA3
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> WifiSecurity.EAP_WPA3_192
    WifiInfo.SECURITY_TYPE_OWE -> WifiSecurity.OWE
    WifiInfo.SECURITY_TYPE_WAPI_PSK -> WifiSecurity.WAPI_PSK
    WifiInfo.SECURITY_TYPE_WAPI_CERT -> WifiSecurity.WAPI_CERT
    WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2 -> WifiSecurity.PASSPOINT_R1R2
    WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> WifiSecurity.PASSPOINT_R3
    WifiInfo.SECURITY_TYPE_DPP -> WifiSecurity.DPP
    else -> WifiSecurity.UNKNOWN
}

fun formatAgo(nowMillis: Long, thenMillis: Long): String {
    val delta = (nowMillis - thenMillis).coerceAtLeast(0L)
    val seconds = delta / 1000
    return when {
        seconds < 5 -> "刚刚"
        seconds < 60 -> "$seconds 秒前"
        seconds < 3600 -> "${seconds / 60} 分钟前"
        else -> "${seconds / 3600} 小时前"
    }
}

fun formatMbps(v: Int?): String = if (v == null) "—" else "$v Mbps"

fun formatFrequency(mhz: Int): String = "$mhz MHz"

fun formatMhzDouble(v: Double?): String =
    if (v == null) "—" else String.format(java.util.Locale.US, "%.2f MHz", v)
