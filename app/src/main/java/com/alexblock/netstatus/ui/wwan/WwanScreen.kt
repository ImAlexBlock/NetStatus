package com.alexblock.netstatus.ui.wwan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.alexblock.netstatus.data.telephony.CellDetails
import com.alexblock.netstatus.data.telephony.WwanSnapshot
import com.alexblock.netstatus.ui.components.DiagnosticList
import com.alexblock.netstatus.ui.components.EmptyState
import com.alexblock.netstatus.ui.components.LastUpdatedText
import com.alexblock.netstatus.ui.components.LevelBars
import com.alexblock.netstatus.ui.components.LocationBanner
import com.alexblock.netstatus.ui.components.MetricGrid
import com.alexblock.netstatus.ui.components.MetricRow
import com.alexblock.netstatus.ui.components.ModeSwitcher
import com.alexblock.netstatus.ui.components.PermissionCard
import com.alexblock.netstatus.ui.components.SectionCard
import com.alexblock.netstatus.ui.components.SignalRing
import com.alexblock.netstatus.ui.components.StatusPill
import com.alexblock.netstatus.util.REQUIRED_WWAN
import com.alexblock.netstatus.util.missingOf
import com.alexblock.netstatus.util.permissionLabel
import com.alexblock.netstatus.util.formatMhzDouble
import com.alexblock.netstatus.util.levelLabel
import com.alexblock.netstatus.util.openAppSettings
import com.alexblock.netstatus.util.openLocationSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WwanScreen(vm: WwanViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
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

            item { WwanHero(snapshot) }

            if (!snapshot.hasTelephony) {
                item { EmptyState("此设备不支持移动网络", "未检测到电话功能") }
            }

            if (snapshot.simState != "就绪" && snapshot.networkOperatorName.isNullOrBlank()) {
                item { EmptyState("未检测到 SIM 卡", "插入 SIM 卡后刷新") }
            }

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
                        message = "读取基站与信号信息需要电话状态和精确位置权限",
                        missing = context.missingOf(REQUIRED_WWAN).map { permissionLabel(it) },
                        showSettings = requested,
                        onRequest = {
                            requested = true
                            permissionLauncher.launch(REQUIRED_WWAN)
                        },
                        onOpenSettings = { context.startActivity(context.openAppSettings()) },
                    )
                }
            } else if (mode == 0) {
                overviewContent(state, vm)
            } else {
                professionalContent(state, vm)
            }
        }
    }
}

@Composable
private fun WwanHero(snapshot: WwanSnapshot) {
    val operatorName = snapshot.networkOperatorName
        ?: snapshot.simOperatorName
        ?: "无服务"
    val status: CheckStatus
    when {
        !snapshot.permissionsGranted -> status = CheckStatus.WARN
        snapshot.registered -> status = CheckStatus.PASS
        snapshot.simState == "就绪" -> status = CheckStatus.WARN
        else -> status = CheckStatus.FAIL
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
                    text = "移动数据",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = if (!snapshot.permissionsGranted) "需要权限" else snapshot.displayTech,
                    status = status,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = operatorName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    val plmn = snapshot.cells.firstOrNull { it.registered }?.plmn
                    Text(
                        text = buildString {
                            append(snapshot.displayTech)
                            append(" · ")
                            append(plmn ?: "—")
                            append(" · ")
                            append(snapshot.dataStateLabel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (!snapshot.permissionsGranted) {
                    SignalRing(
                        level = 0,
                        value = "—",
                        caption = "需要权限",
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                    )
                } else {
                    SignalRing(
                        level = snapshot.signalLevel,
                        value = snapshot.signalDbm?.let { "$it dBm" } ?: "—",
                        caption = levelLabel(snapshot.signalLevel),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.overviewContent(state: WwanUiState, vm: WwanViewModel) {
    val snapshot = state.snapshot
    val serving = snapshot.cells.firstOrNull { it.registered }

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
        SectionCard(title = "关键信息") {
            MetricGrid(
                listOf(
                    "制式" to snapshot.displayTech,
                    "频段" to (serving?.band ?: "—"),
                    "小区标识" to (serving?.cellId ?: "—"),
                    "PLMN" to (serving?.plmn ?: "—"),
                    "运营商" to (snapshot.networkOperatorName ?: snapshot.simOperatorName ?: "—"),
                    "数据状态" to snapshot.dataStateLabel,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "5G SA/NSA 标识由系统按运营商策略显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.professionalContent(state: WwanUiState, vm: WwanViewModel) {
    val snapshot = state.snapshot
    val serving = snapshot.cells.firstOrNull { it.registered }
    val network = snapshot.network

    item {
        SectionCard(title = "服务小区") {
            Column {
                MetricRow("制式", serving?.tech?.label ?: snapshot.displayTech, mono = false)
                MetricRow("运营商", serving?.operator ?: snapshot.networkOperatorName ?: "—", mono = false)
                MetricRow("PLMN", serving?.plmn ?: "—")
                MetricRow("频段", serving?.band ?: "—")
                MetricRow("ARFCN/EARFCN", serving?.arfcn?.toString() ?: "—")
                MetricRow("频率", formatMhzDouble(serving?.frequencyMhz))
                MetricRow(
                    "带宽",
                    serving?.bandwidthMhz?.let { String.format(java.util.Locale.US, "%.1f MHz", it) } ?: "—",
                )
                MetricRow("小区标识", serving?.cellId ?: "—")
                MetricRow("PCI", serving?.pci?.toString() ?: "—")
                MetricRow("TAC", serving?.tac?.toString() ?: "—")
                MetricRow("RSRP", (serving?.ssRsrp ?: serving?.rsrp)?.let { "$it dBm" } ?: "—")
                MetricRow("RSRQ", (serving?.ssRsrq ?: serving?.rsrq)?.let { "$it dB" } ?: "—")
                MetricRow("SINR", (serving?.ssSinr ?: serving?.rssnr)?.let { "$it dB" } ?: "—")
                MetricRow("信号等级", levelLabel(snapshot.signalLevel), mono = false)
                MetricRow("漫游", if (snapshot.roaming) "是" else "否", mono = false)
            }
            if (serving == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "未获取到小区信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item {
        SectionCard(
            title = "小区列表",
            subtitle = "${snapshot.cells.size} 个",
            action = {
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            },
        ) {
            Text(
                text = "部分运营商或机型会限制邻区信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (snapshot.cells.isEmpty()) {
        item {
            Text(
                text = "未获取到小区信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(
            snapshot.cells,
            key = { "${it.tech}|${it.plmn}|${it.tac}|${it.cellId}|${it.pci}|${it.arfcn}" },
        ) { cell ->
            CellRow(cell)
        }
    }

    item {
        SectionCard(title = "数据连接") {
            Column {
                MetricRow(
                    "IP 地址",
                    listOfNotNull(
                        network?.ipv4?.joinToString(", ")?.takeIf { it.isNotBlank() },
                        network?.ipv6?.joinToString(", ")?.takeIf { it.isNotBlank() },
                    ).joinToString("  ").ifBlank { "—" },
                )
                MetricRow("网关", network?.gateway ?: "—")
                MetricRow(
                    "DNS",
                    network?.dns?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "—",
                )
                MetricRow(
                    "网络验证",
                    when {
                        network == null -> "—"
                        network.validated -> "已验证"
                        else -> "未验证"
                    },
                )
                MetricRow(
                    "计费网络",
                    when {
                        network == null -> "—"
                        network.metered -> "是"
                        else -> "否"
                    },
                )
            }
        }
    }
}

@Composable
private fun CellRow(cell: CellDetails) {
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
                        StatusPill(cell.tech.label, CheckStatus.UNKNOWN)
                        if (cell.registered) {
                            Spacer(Modifier.padding(start = 6.dp))
                            StatusPill("服务小区", CheckStatus.PASS)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(cell.band ?: "—")
                            append(" · PCI ")
                            append(cell.pci?.toString() ?: "—")
                            append(" · RSRP ")
                            append((cell.ssRsrp ?: cell.rsrp)?.let { "$it dBm" } ?: "—")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LevelBars(cell.level)
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
                    MetricRow("PLMN", cell.plmn ?: "—")
                    MetricRow("运营商", cell.operator ?: "—", mono = false)
                    MetricRow("TAC", cell.tac?.toString() ?: "—")
                    MetricRow("NCI/CI", cell.cellId ?: "—")
                    MetricRow("ARFCN", cell.arfcn?.toString() ?: "—")
                    MetricRow("频率", formatMhzDouble(cell.frequencyMhz))
                    MetricRow(
                        "带宽",
                        cell.bandwidthMhz?.let {
                            String.format(java.util.Locale.US, "%.1f MHz", it)
                        } ?: "—",
                    )
                    MetricRow("RSRQ", (cell.ssRsrq ?: cell.rsrq)?.let { "$it dB" } ?: "—")
                    MetricRow("SINR", (cell.ssSinr ?: cell.rssnr)?.let { "$it dB" } ?: "—")
                    MetricRow("连接状态", cell.connectionStatus, mono = false)
                }
            }
        }
    }
}
