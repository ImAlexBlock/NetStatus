package com.alexblock.netstatus.ui.wlan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexblock.netstatus.data.CheckStatus
import com.alexblock.netstatus.data.wifi.WifiAp
import com.alexblock.netstatus.data.wifi.WifiConnection
import com.alexblock.netstatus.data.wifi.WifiSnapshot
import com.alexblock.netstatus.ui.components.DiagnosticList
import com.alexblock.netstatus.ui.components.EmptyState
import com.alexblock.netstatus.ui.components.LastUpdatedText
import com.alexblock.netstatus.ui.components.LocationBanner
import com.alexblock.netstatus.ui.components.MetricGrid
import com.alexblock.netstatus.ui.components.MetricRow
import com.alexblock.netstatus.ui.components.ModeSwitcher
import com.alexblock.netstatus.ui.components.PermissionCard
import com.alexblock.netstatus.ui.components.ScanAction
import com.alexblock.netstatus.ui.components.SectionCard
import com.alexblock.netstatus.ui.components.SignalRing
import com.alexblock.netstatus.ui.components.WifiSpectrum
import com.alexblock.netstatus.ui.components.StatusPill
import com.alexblock.netstatus.util.REQUIRED_WIFI
import com.alexblock.netstatus.util.missingOf
import com.alexblock.netstatus.util.permissionLabel
import com.alexblock.netstatus.util.WifiBand
import com.alexblock.netstatus.util.WifiBands
import com.alexblock.netstatus.util.formatMbps
import com.alexblock.netstatus.util.openAppSettings
import com.alexblock.netstatus.util.openLocationSettings
import com.alexblock.netstatus.util.rssiLevel
import com.alexblock.netstatus.util.rssiQuality
import com.alexblock.netstatus.util.securityLabel
import com.alexblock.netstatus.util.standardLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WlanScreen(vm: WlanViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var bandFilter by rememberSaveable { mutableIntStateOf(0) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val snapshot = state.snapshot
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    LifecycleResumeEffect(Unit) {
        vm.start()
        onPauseOrDispose { vm.stop() }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = vm::refresh,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ModeSwitcher(selected = mode, onSelected = { mode = it }) }

            item { WlanHero(snapshot = snapshot) }

            if (!snapshot.locationEnabled) {
                item {
                    LocationBanner(onOpenSettings = {
                        context.startActivity(context.openLocationSettings())
                    })
                }
            }

            if (!snapshot.permissionsGranted) {
                item {
                    PermissionCard(
                        title = "需要权限",
                        message = "读取 Wi-Fi 名称与扫描结果需要精确位置和附近的设备权限",
                        missing = context.missingOf(REQUIRED_WIFI).map { permissionLabel(it) },
                        showSettings = requested,
                        onRequest = {
                            requested = true
                            permissionLauncher.launch(REQUIRED_WIFI)
                        },
                        onOpenSettings = { context.startActivity(context.openAppSettings()) },
                    )
                }
            } else {
                if (!snapshot.wifiEnabled) {
                    item { EmptyState("Wi-Fi 已关闭", "请在系统设置中开启 WLAN") }
                }
                if (mode == 0) {
                    overviewContent(state, vm)
                } else {
                    professionalContent(state, vm, bandFilter, onBandFilter = { bandFilter = it })
                }
            }
        }
    }
}

@Composable
private fun WlanHero(snapshot: WifiSnapshot) {
    val connection = snapshot.connection
    val statusText: String
    val status: CheckStatus
    when {
        !snapshot.permissionsGranted -> {
            statusText = "需要权限"
            status = CheckStatus.WARN
        }
        !snapshot.wifiEnabled -> {
            statusText = "Wi-Fi 已关闭"
            status = CheckStatus.FAIL
        }
        connection != null -> {
            statusText = "已连接"
            status = CheckStatus.PASS
        }
        else -> {
            statusText = "未连接"
            status = CheckStatus.WARN
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "WLAN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(statusText, status)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = connection?.ssid ?: "未知网络",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (connection != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = heroSubtitle(connection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "速率 " + formatMbps(connection.txMbps ?: connection.rxMbps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (connection != null) {
                    SignalRing(
                        level = rssiLevel(connection.rssi),
                        value = "${connection.rssi} dBm",
                        caption = rssiQuality(connection.rssi),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                    )
                } else {
                    SignalRing(
                        level = 0,
                        value = "—",
                        caption = "未连接",
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}

private fun heroSubtitle(connection: WifiConnection): String = buildString {
    append(WifiBands.bandOf(connection.frequencyMhz).label)
    val channel = WifiBands.channelOf(connection.frequencyMhz)
    if (channel != null) append(" · 信道 $channel")
    append(" · ")
    append(standardLabel(connection.standard))
}

private fun LazyListScope.overviewContent(state: WlanUiState, vm: WlanViewModel) {
    val snapshot = state.snapshot
    val connection = snapshot.connection

    item {
        SectionCard(
            title = "网络诊断",
            action = {
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            },
        ) {
            Column {
                DiagnosticList(state.checks)
                Spacer(Modifier.height(10.dp))
                LastUpdatedText("上次更新", state.probe?.atMillis)
            }
        }
    }

    item {
        val channel = connection?.let { WifiBands.channelOf(it.frequencyMhz) }
        SectionCard(title = "关键信息") {
            MetricGrid(
                listOf(
                    "频段" to (connection?.let { WifiBands.bandOf(it.frequencyMhz).label } ?: "—"),
                    "信道" to (channel?.toString() ?: "—"),
                    "协议" to (connection?.let { standardLabel(it.standard) } ?: "—"),
                    "安全" to (connection?.let { securityLabel(it.security) } ?: "—"),
                    "链路速率" to formatMbps(connection?.txMbps ?: connection?.rxMbps),
                    "IP" to (snapshot.network?.ipv4?.firstOrNull() ?: "—"),
                ),
            )
        }
    }
}

private fun LazyListScope.professionalContent(
    state: WlanUiState,
    vm: WlanViewModel,
    bandFilter: Int,
    onBandFilter: (Int) -> Unit,
) {
    val snapshot = state.snapshot
    val connection = snapshot.connection

    item {
        SectionCard(title = "连接详情") {
            val channel = connection?.let { WifiBands.channelOf(it.frequencyMhz) }
            Column {
                MetricRow("SSID", connection?.ssid ?: "—")
                MetricRow("BSSID", connection?.bssid ?: "—")
                MetricRow(
                    "信号强度",
                    connection?.let { "${it.rssi} dBm · ${rssiQuality(it.rssi)}" } ?: "—",
                )
                MetricRow("频段", connection?.let { WifiBands.bandOf(it.frequencyMhz).label } ?: "—")
                MetricRow("信道", channel?.toString() ?: "—")
                MetricRow("信道宽度", connection?.channelWidth?.let { WifiBands.widthLabel(it) } ?: "—")
                MetricRow("Wi-Fi 协议", connection?.let { standardLabel(it.standard) } ?: "—")
                MetricRow("安全类型", connection?.let { securityLabel(it.security) } ?: "—")
                MetricRow("发送速率", formatMbps(connection?.txMbps))
                MetricRow("接收速率", formatMbps(connection?.rxMbps))
                MetricRow("最大发送", formatMbps(connection?.maxTxMbps))
                MetricRow("最大接收", formatMbps(connection?.maxRxMbps))
                MetricRow(
                    "IP 地址",
                    snapshot.network?.ipv4?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "—",
                )
                MetricRow(
                    "IPv6",
                    snapshot.network?.ipv6?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "—",
                )
                MetricRow("网关", snapshot.network?.gateway ?: "—")
                MetricRow(
                    "DNS",
                    snapshot.network?.dns?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "—",
                )
                MetricRow(
                    "网络验证",
                    when {
                        snapshot.network == null -> "—"
                        snapshot.network.validated -> "已验证"
                        else -> "未验证"
                    },
                )
                MetricRow(
                    "计费网络",
                    when {
                        snapshot.network == null -> "—"
                        snapshot.network.metered -> "是"
                        else -> "否"
                    },
                )
                MetricRow("隐藏 SSID", if (connection?.hiddenSsid == true) "是" else "否")
                MetricRow("连接状态", connection?.supplicantState ?: "—")
            }
        }
    }

    item {
        SectionCard(title = "频谱分布") {
            WifiSpectrum(accessPoints = snapshot.accessPoints)
        }
    }

    item {
        SectionCard(
            title = "附近的网络",
            subtitle = "${snapshot.accessPoints.size} 个",
            action = { ScanAction(scanning = snapshot.scanning, onScan = vm::scan) },
        ) {
            Column {
                LastUpdatedText("上次扫描", snapshot.lastScanAtMillis)
                if (snapshot.scanThrottled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "系统已限制扫描频率，显示缓存结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = bandFilter == 0,
                        onClick = { onBandFilter(0) },
                        label = { Text("全部 ${snapshot.accessPoints.size}", maxLines = 1) },
                    )
                    FilterChip(
                        selected = bandFilter == 1,
                        onClick = { onBandFilter(1) },
                        label = {
                            Text(
                                "2.4 GHz ${snapshot.accessPoints.count { it.band == WifiBand.BAND_2G4 }}",
                                maxLines = 1,
                            )
                        },
                    )
                    FilterChip(
                        selected = bandFilter == 2,
                        onClick = { onBandFilter(2) },
                        label = {
                            Text(
                                "5 GHz ${snapshot.accessPoints.count { it.band == WifiBand.BAND_5G }}",
                                maxLines = 1,
                            )
                        },
                    )
                    FilterChip(
                        selected = bandFilter == 3,
                        onClick = { onBandFilter(3) },
                        label = {
                            Text(
                                "6 GHz ${snapshot.accessPoints.count { it.band == WifiBand.BAND_6G }}",
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
    }

    val visible = when (bandFilter) {
        1 -> snapshot.accessPoints.filter { it.band == WifiBand.BAND_2G4 }
        2 -> snapshot.accessPoints.filter { it.band == WifiBand.BAND_5G }
        3 -> snapshot.accessPoints.filter { it.band == WifiBand.BAND_6G }
        else -> snapshot.accessPoints
    }

    if (visible.isEmpty()) {
        item {
            Text(
                text = "该频段暂无网络",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(visible, key = { it.bssid + "|" + it.frequencyMhz }) { ap ->
            ApRow(ap)
        }
    }

    item {
        SectionCard(title = "本机能力") {
            Column {
                MetricRow("Wi-Fi 状态", if (snapshot.wifiEnabled) "已开启" else "已关闭", mono = false)
                MetricRow("2.4 GHz 支持", yesNo(state.bandSupport["2.4 GHz"]), mono = false)
                MetricRow("5 GHz 支持", yesNo(state.bandSupport["5 GHz"]), mono = false)
                MetricRow("6 GHz 支持", yesNo(state.bandSupport["6 GHz"]), mono = false)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "扫描频率受系统限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun yesNo(value: Boolean?): String = when (value) {
    true -> "是"
    false -> "否"
    null -> "—"
}

@Composable
private fun ApRow(ap: WifiAp) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ap.ssid,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (ap.connected) {
                            Spacer(Modifier.padding(start = 6.dp))
                            StatusPill("已连接", CheckStatus.PASS)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${ap.rssi} dBm")
                            append(" · ${ap.band.label}")
                            if (ap.channel != null) append(" · 信道 ${ap.channel}")
                            append(" · ")
                            append(ap.width?.let { WifiBands.widthLabel(it) } ?: "未知")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${standardLabel(ap.standard)} · ${ap.security}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    MetricRow("BSSID", ap.bssid)
                    MetricRow("频率", "${ap.frequencyMhz} MHz")
                    MetricRow("信道宽度", ap.width?.let { WifiBands.widthLabel(it) } ?: "未知")
                    MetricRow("协议", standardLabel(ap.standard))
                    MetricRow("安全", ap.security)
                }
            }
        }
    }
}
