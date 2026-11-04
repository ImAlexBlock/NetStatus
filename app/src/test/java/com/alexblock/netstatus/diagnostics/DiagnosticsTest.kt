package com.alexblock.netstatus.diagnostics

import com.alexblock.netstatus.data.CheckStatus
import com.alexblock.netstatus.data.NetworkDetails
import com.alexblock.netstatus.data.ProbeResult
import com.alexblock.netstatus.data.telephony.WwanSnapshot
import com.alexblock.netstatus.data.wifi.WifiConnection
import com.alexblock.netstatus.data.wifi.WifiSnapshot
import com.alexblock.netstatus.util.WifiSecurity
import com.alexblock.netstatus.util.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    private fun wifiSnapshot(
        wifiEnabled: Boolean = true,
        connection: WifiConnection? = null,
        network: NetworkDetails? = null,
    ) = WifiSnapshot(
        wifiEnabled = wifiEnabled,
        connection = connection,
        network = network,
        accessPoints = emptyList(),
        lastScanAtMillis = null,
        scanning = false,
        scanThrottled = false,
        locationEnabled = true,
        permissionsGranted = true,
        error = null,
    )

    private fun connection() = WifiConnection(
        ssid = "HomeNet",
        bssid = "AA:BB:CC:DD:EE:FF",
        rssi = -15,
        frequencyMhz = 2412,
        standard = WifiStandard.AX,
        security = WifiSecurity.PSK,
        txMbps = 172,
        rxMbps = 172,
        maxTxMbps = 344,
        maxRxMbps = 344,
        channelWidth = 0,
        supplicantState = "COMPLETED",
        hiddenSsid = false,
    )

    private fun validatedNetwork() = NetworkDetails(
        ipv4 = listOf("192.168.1.20/24"),
        ipv6 = emptyList(),
        gateway = "192.168.1.1",
        dns = listOf("192.168.1.1"),
        validated = true,
        captivePortal = false,
        internet = true,
        metered = false,
    )

    @Test
    fun wifiOffReportsFailure() {
        val checks = WlanDiagnostics.evaluate(wifiSnapshot(wifiEnabled = false), null)
        val switch = checks.first { it.title == "Wi-Fi 开关" }
        assertEquals(CheckStatus.FAIL, switch.status)
        assertEquals("Wi-Fi 已关闭", switch.detail)
        assertEquals("网络连接", checks[1].title)
        assertEquals(CheckStatus.WARN, checks[1].status)
    }

    @Test
    fun wifiHealthyConnectionPassesInternetAndProbe() {
        val probe = ProbeResult(
            endpoint = "https://connectivitycheck.gstatic.com/generate_204",
            latencyMs = 42,
            success = true,
            atMillis = 1_000L,
        )
        val checks = WlanDiagnostics.evaluate(
            wifiSnapshot(connection = connection(), network = validatedNetwork()),
            probe,
        )
        assertEquals(CheckStatus.PASS, checks.first { it.title == "互联网验证" }.status)
        assertEquals(CheckStatus.PASS, checks.first { it.title == "延迟探测" }.status)
        assertEquals(CheckStatus.PASS, checks.first { it.title == "信号强度" }.status)
        assertEquals(CheckStatus.PASS, checks.first { it.title == "链路速率" }.status)
        assertEquals(8, checks.size)
    }

    @Test
    fun captivePortalFails() {
        val network = validatedNetwork().copy(validated = false, captivePortal = true)
        val checks = WlanDiagnostics.evaluate(
            wifiSnapshot(connection = connection(), network = network),
            null,
        )
        val internet = checks.first { it.title == "互联网验证" }
        assertEquals(CheckStatus.FAIL, internet.status)
        assertEquals("需要网页认证", internet.detail)
    }

    @Test
    fun wwanRomingWarns() {
        val snapshot = WwanSnapshot(
            simState = "就绪",
            simOperatorName = "示例运营商",
            networkOperatorName = "示例运营商",
            dataNetworkTypeLabel = "5G NR",
            displayTech = "5G SA",
            serviceStateLabel = "已注册",
            registered = true,
            roaming = true,
            dataEnabled = true,
            dataStateLabel = "已连接",
            dataActivityLabel = "无",
            signalDbm = -81,
            signalLevel = 3,
            network = validatedNetwork(),
            cells = emptyList(),
            lastUpdateMillis = 0L,
            permissionsGranted = true,
            locationEnabled = true,
            hasTelephony = true,
            error = null,
        )
        val checks = WwanDiagnostics.evaluate(snapshot, null)
        val roaming = checks.first { it.title == "漫游" }
        assertEquals(CheckStatus.WARN, roaming.status)
        assertEquals(CheckStatus.PASS, checks.first { it.title == "网络注册" }.status)
        assertEquals(CheckStatus.PASS, checks.first { it.title == "信号强度" }.status)
        assertEquals(CheckStatus.UNKNOWN, checks.first { it.title == "延迟探测" }.status)
    }

    @Test
    fun wwanWithoutNetworkIsUnknownInternet() {
        val snapshot = WwanSnapshot(
            simState = "就绪",
            simOperatorName = "示例运营商",
            networkOperatorName = "示例运营商",
            dataNetworkTypeLabel = "5G NR",
            displayTech = "5G SA",
            serviceStateLabel = "已注册",
            registered = true,
            roaming = false,
            dataEnabled = true,
            dataStateLabel = "已连接",
            dataActivityLabel = "无",
            signalDbm = -81,
            signalLevel = 3,
            network = null,
            cells = emptyList(),
            lastUpdateMillis = 0L,
            permissionsGranted = true,
            locationEnabled = true,
            hasTelephony = true,
            error = null,
        )
        val checks = WwanDiagnostics.evaluate(snapshot, null)
        val internet = checks.first { it.title == "互联网验证" }
        assertEquals(CheckStatus.UNKNOWN, internet.status)
        assertTrue(checks.none { it.status == CheckStatus.FAIL })
    }
}
