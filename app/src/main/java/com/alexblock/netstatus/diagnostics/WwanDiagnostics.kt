package com.alexblock.netstatus.diagnostics

import com.alexblock.netstatus.data.CheckStatus
import com.alexblock.netstatus.data.DiagnosticCheck
import com.alexblock.netstatus.data.ProbeResult
import com.alexblock.netstatus.data.telephony.WwanSnapshot
import com.alexblock.netstatus.util.levelLabel

object WwanDiagnostics {

    fun evaluate(snapshot: WwanSnapshot, probe: ProbeResult?): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        val network = snapshot.network

        checks += when (snapshot.dataEnabled) {
            true -> DiagnosticCheck("移动数据", "已开启", CheckStatus.PASS)
            false -> DiagnosticCheck("移动数据", "已关闭", CheckStatus.FAIL)
            null -> DiagnosticCheck("移动数据", "未获取", CheckStatus.UNKNOWN)
        }

        checks += if (snapshot.registered) {
            DiagnosticCheck("网络注册", snapshot.serviceStateLabel, CheckStatus.PASS)
        } else {
            DiagnosticCheck("网络注册", snapshot.serviceStateLabel, CheckStatus.FAIL)
        }

        // Idle or suspended cellular data on a registered network is not a fault.
        checks += when (snapshot.dataStateLabel) {
            "已连接" -> DiagnosticCheck("数据连接", "已连接", CheckStatus.PASS)
            "连接中" -> DiagnosticCheck("数据连接", "连接中", CheckStatus.WARN)
            "已挂起" -> DiagnosticCheck("数据连接", "已挂起", CheckStatus.WARN)
            "已断开" -> DiagnosticCheck("数据连接", "已断开", CheckStatus.WARN)
            else -> DiagnosticCheck("数据连接", snapshot.dataStateLabel, CheckStatus.UNKNOWN)
        }

        checks += if (snapshot.signalDbm == null) {
            DiagnosticCheck("信号强度", "未获取", CheckStatus.UNKNOWN)
        } else {
            val status = when {
                snapshot.signalLevel >= 3 -> CheckStatus.PASS
                snapshot.signalLevel == 2 -> CheckStatus.WARN
                else -> CheckStatus.FAIL
            }
            DiagnosticCheck(
                "信号强度",
                "${snapshot.signalDbm} dBm · ${levelLabel(snapshot.signalLevel)}",
                status,
            )
        }

        checks += if (snapshot.roaming) {
            DiagnosticCheck("漫游", "漫游中", CheckStatus.WARN)
        } else {
            DiagnosticCheck("漫游", "未漫游", CheckStatus.PASS)
        }

        checks += if (snapshot.simState == "就绪") {
            DiagnosticCheck("SIM 卡", snapshot.simOperatorName ?: "就绪", CheckStatus.PASS)
        } else {
            DiagnosticCheck("SIM 卡", snapshot.simState, CheckStatus.FAIL)
        }

        checks += when {
            network == null -> DiagnosticCheck("互联网验证", "未获取", CheckStatus.UNKNOWN)
            network.captivePortal -> DiagnosticCheck("互联网验证", "需要网页认证", CheckStatus.FAIL)
            network.validated -> DiagnosticCheck("互联网验证", "已验证联网", CheckStatus.PASS)
            network.internet -> DiagnosticCheck("互联网验证", "有网络但未验证", CheckStatus.WARN)
            else -> DiagnosticCheck("互联网验证", "无互联网", CheckStatus.FAIL)
        }

        checks += when {
            probe == null -> DiagnosticCheck("延迟探测", "未测试", CheckStatus.UNKNOWN)
            !probe.success -> DiagnosticCheck("延迟探测", "探测失败", CheckStatus.FAIL)
            probe.latencyMs <= 150 -> DiagnosticCheck("延迟探测", "${probe.latencyMs} ms", CheckStatus.PASS)
            else -> DiagnosticCheck("延迟探测", "${probe.latencyMs} ms · 延迟较高", CheckStatus.WARN)
        }

        return checks
    }
}
