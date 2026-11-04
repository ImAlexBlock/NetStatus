package com.alexblock.netstatus.diagnostics

import com.alexblock.netstatus.data.CheckStatus
import com.alexblock.netstatus.data.DiagnosticCheck
import com.alexblock.netstatus.data.ProbeResult
import com.alexblock.netstatus.data.wifi.WifiSnapshot
import com.alexblock.netstatus.util.rssiLevel
import com.alexblock.netstatus.util.rssiQuality

object WlanDiagnostics {

    fun evaluate(snapshot: WifiSnapshot, probe: ProbeResult?): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        val conn = snapshot.connection
        val network = snapshot.network

        checks += if (!snapshot.wifiEnabled) {
            DiagnosticCheck("Wi-Fi 开关", "Wi-Fi 已关闭", CheckStatus.FAIL)
        } else {
            DiagnosticCheck("Wi-Fi 开关", "Wi-Fi 已开启", CheckStatus.PASS)
        }

        checks += if (conn == null) {
            DiagnosticCheck("网络连接", "未连接网络", CheckStatus.WARN)
        } else {
            DiagnosticCheck("网络连接", "已连接 ${conn.ssid}", CheckStatus.PASS)
        }

        checks += if (conn == null) {
            DiagnosticCheck("信号强度", "无信号", CheckStatus.UNKNOWN)
        } else {
            val level = rssiLevel(conn.rssi)
            val status = when {
                level >= 3 -> CheckStatus.PASS
                level == 2 -> CheckStatus.WARN
                else -> CheckStatus.FAIL
            }
            DiagnosticCheck("信号强度", "${conn.rssi} dBm · ${rssiQuality(conn.rssi)}", status)
        }

        val rate = conn?.let { listOfNotNull(it.txMbps, it.rxMbps).maxOrNull() }
        checks += when {
            conn == null || rate == null -> DiagnosticCheck("链路速率", "未获取", CheckStatus.UNKNOWN)
            rate >= 100 -> DiagnosticCheck("链路速率", "$rate Mbps", CheckStatus.PASS)
            rate >= 20 -> DiagnosticCheck("链路速率", "$rate Mbps", CheckStatus.WARN)
            else -> DiagnosticCheck("链路速率", "$rate Mbps", CheckStatus.FAIL)
        }

        val hasIp = network != null && (network.ipv4.isNotEmpty() || network.ipv6.isNotEmpty())
        checks += if (hasIp) {
            DiagnosticCheck("IP 地址", "已获取地址", CheckStatus.PASS)
        } else {
            DiagnosticCheck("IP 地址", "未获取地址", CheckStatus.FAIL)
        }

        val hasDns = network != null && network.dns.isNotEmpty()
        checks += if (hasDns) {
            DiagnosticCheck("DNS", network!!.dns.joinToString(", "), CheckStatus.PASS)
        } else {
            DiagnosticCheck("DNS", "未获取 DNS", CheckStatus.WARN)
        }

        checks += when {
            network == null -> DiagnosticCheck("互联网验证", "无网络", CheckStatus.FAIL)
            network.captivePortal -> DiagnosticCheck("互联网验证", "需要网页认证", CheckStatus.FAIL)
            network.validated -> DiagnosticCheck("互联网验证", "已验证联网", CheckStatus.PASS)
            network.internet -> DiagnosticCheck("互联网验证", "有网络但未验证", CheckStatus.WARN)
            else -> DiagnosticCheck("互联网验证", "无互联网", CheckStatus.FAIL)
        }

        checks += when {
            probe == null -> DiagnosticCheck("延迟探测", "未测试", CheckStatus.UNKNOWN)
            !probe.success -> DiagnosticCheck("延迟探测", "探测失败", CheckStatus.FAIL)
            probe.latencyMs <= 150 -> DiagnosticCheck("延迟探测", "${probe.latencyMs} ms", CheckStatus.PASS)
            probe.latencyMs <= 500 -> DiagnosticCheck("延迟探测", "${probe.latencyMs} ms · 延迟较高", CheckStatus.WARN)
            else -> DiagnosticCheck("延迟探测", "${probe.latencyMs} ms · 延迟较高", CheckStatus.WARN)
        }

        return checks
    }
}
