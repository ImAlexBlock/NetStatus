package com.alexblock.netstatus.data.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import com.alexblock.netstatus.data.NetworkDetails
import com.alexblock.netstatus.util.NrArfcn
import com.alexblock.netstatus.util.networkTypeLabel
import com.alexblock.netstatus.data.wifi.gatewayOf
import com.alexblock.netstatus.data.wifi.ipv4Of
import com.alexblock.netstatus.data.wifi.ipv6Of
import com.alexblock.netstatus.util.hasFineLocation
import com.alexblock.netstatus.util.hasAll
import com.alexblock.netstatus.util.locationEnabled
import com.alexblock.netstatus.util.REQUIRED_WWAN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class TelephonyRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val baseManager: TelephonyManager? =
        appContext.getSystemService(TelephonyManager::class.java)
    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val telephonyManager: TelephonyManager? = try {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (subId >= 0 && baseManager != null) {
            baseManager.createForSubscriptionId(subId)
        } else {
            baseManager
        }
    } catch (e: Exception) {
        baseManager
    }

    private val hasTelephony: Boolean = appContext.packageManager
        .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private val _state = MutableStateFlow(
        WwanSnapshot(
            simState = "未知",
            simOperatorName = null,
            networkOperatorName = null,
            dataNetworkTypeLabel = "未知",
            displayTech = "未知",
            serviceStateLabel = "未知",
            registered = false,
            roaming = false,
            dataEnabled = null,
            dataStateLabel = "未知",
            dataActivityLabel = null,
            signalDbm = null,
            signalLevel = 0,
            network = null,
            cells = emptyList(),
            lastUpdateMillis = System.currentTimeMillis(),
            permissionsGranted = false,
            locationEnabled = false,
            hasTelephony = hasTelephony,
            error = null,
        ),
    )
    val state: StateFlow<WwanSnapshot> = _state.asStateFlow()

    private var started = false
    private var callback: TelephonyCallback? = null
    private var lastServiceState: android.telephony.ServiceState? = null
    private var lastDisplayInfo: TelephonyDisplayInfo? = null
    private var lastCells: List<CellInfo> = emptyList()

    fun hasPermissions(): Boolean = context.hasAll(REQUIRED_WWAN) && context.hasFineLocation()

    fun start() {
        if (started) return
        started = true

        if (!hasPermissions()) {
            _state.update {
                it.copy(permissionsGranted = false, locationEnabled = context.locationEnabled())
            }
            return
        }

        val manager = telephonyManager ?: return
        val cb = object : TelephonyCallback(),
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.DisplayInfoListener,
            TelephonyCallback.CellInfoListener {

            override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) {
                lastServiceState = serviceState
                publish()
            }

            override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                lastDisplayInfo = displayInfo
                publish()
            }

            override fun onCellInfoChanged(cells: MutableList<CellInfo>) {
                lastCells = cells
                publish()
            }
        }
        callback = cb
        try {
            manager.registerTelephonyCallback(context.mainExecutor, cb)
        } catch (e: SecurityException) {
            _state.update { it.copy(error = "缺少电话状态权限") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "电话状态回调注册失败") }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            callback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } catch (e: Exception) {
            // Ignore.
        }
        callback = null
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val manager = telephonyManager
        if (manager == null || !hasTelephony) {
            _state.update { it.copy(hasTelephony = false, permissionsGranted = hasPermissions()) }
            return@withContext
        }

        val granted = hasPermissions()
        if (!granted) {
            val simName = try {
                manager.simOperatorName
            } catch (e: Exception) {
                null
            }
            _state.update {
                it.copy(
                    permissionsGranted = false,
                    simState = simStateLabel(manager),
                    simOperatorName = simName,
                    locationEnabled = context.locationEnabled(),
                )
            }
            return@withContext
        }

        try {
            lastServiceState = manager.serviceState
        } catch (e: SecurityException) {
            _state.update { it.copy(error = "缺少电话状态权限") }
        } catch (e: Exception) {
            // Ignore.
        }

        try {
            val cached = manager.allCellInfo
            if (cached != null && cached.isNotEmpty()) lastCells = cached
        } catch (e: Exception) {
            // Ignore.
        }

        publish()

        try {
            manager.requestCellInfoUpdate(
                context.mainExecutor,
                object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cells: MutableList<CellInfo>) {
                        lastCells = cells
                        publish()
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) {
                        // Keep cached cells.
                    }
                },
            )
        } catch (e: Exception) {
            // Ignore.
        }
    }

    private fun publish() {
        val manager = telephonyManager ?: return
        val serviceState = lastServiceState
        val cells = lastCells.mapNotNull { mapCell(it) }
            .distinctBy { "${it.tech}|${it.plmn}|${it.tac}|${it.cellId}|${it.pci}|${it.arfcn}" }
            .sortedWith(
                compareByDescending<CellDetails> { it.registered }
                    .thenBy { techOrder(it.tech) }
                    .thenByDescending { it.level },
            )

        val networkType = try {
            manager.dataNetworkType
        } catch (e: Exception) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val displayTech = displayTechOf(lastDisplayInfo, networkType)
        val registeredCell = cells.firstOrNull { it.registered }
        val signalDbm = registeredCell?.let { it.ssRsrp ?: it.rsrp }
        val dataStateLabel = dataStateLabel(manager)
        val dataActivityLabel = dataActivityLabel(manager)

        val networkDetails = activeCellularNetwork()

        _state.update {
            it.copy(
                simState = simStateLabel(manager),
                simOperatorName = try {
                    manager.simOperatorName
                } catch (e: Exception) {
                    null
                },
                networkOperatorName = serviceState?.operatorAlphaLong?.toString()
                    ?: try {
                        manager.networkOperatorName
                    } catch (e: Exception) {
                        null
                    },
                dataNetworkTypeLabel = networkTypeLabel(networkType),
                displayTech = displayTech,
                serviceStateLabel = serviceStateLabel(serviceState),
                registered = serviceState?.state == android.telephony.ServiceState.STATE_IN_SERVICE,
                roaming = serviceState?.roaming == true,
                dataEnabled = try {
                    manager.isDataEnabled
                } catch (e: Exception) {
                    null
                },
                dataStateLabel = dataStateLabel,
                dataActivityLabel = dataActivityLabel,
                signalDbm = signalDbm,
                signalLevel = registeredCell?.level ?: 0,
                network = networkDetails,
                cells = cells,
                lastUpdateMillis = System.currentTimeMillis(),
                permissionsGranted = true,
                locationEnabled = context.locationEnabled(),
                hasTelephony = true,
                error = null,
            )
        }
    }

    private fun displayTechOf(info: TelephonyDisplayInfo?, networkType: Int): String {
        val override = info?.overrideNetworkType
        return when (override) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G SA+"
            else -> if (networkType == TelephonyManager.NETWORK_TYPE_NR) "5G SA"
            else networkTypeLabel(networkType)
        }
    }

    private fun serviceStateLabel(serviceState: android.telephony.ServiceState?): String =
        when (serviceState?.state) {
            android.telephony.ServiceState.STATE_IN_SERVICE -> "已注册"
            android.telephony.ServiceState.STATE_OUT_OF_SERVICE -> "无服务"
            android.telephony.ServiceState.STATE_EMERGENCY_ONLY -> "仅紧急呼叫"
            android.telephony.ServiceState.STATE_POWER_OFF -> "无线电关闭"
            else -> "未知"
        }

    private fun simStateLabel(manager: TelephonyManager): String = try {
        when (manager.simState) {
            TelephonyManager.SIM_STATE_READY -> "就绪"
            TelephonyManager.SIM_STATE_ABSENT -> "无 SIM 卡"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "需要 PIN"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "需要 PUK"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "网络锁定"
            TelephonyManager.SIM_STATE_NOT_READY -> "未就绪"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "永久停用"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "卡 IO 错误"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "卡受限"
            else -> "未知"
        }
    } catch (e: Exception) {
        "未知"
    }

    private fun dataStateLabel(manager: TelephonyManager): String = try {
        when (manager.dataState) {
            TelephonyManager.DATA_CONNECTED -> "已连接"
            TelephonyManager.DATA_CONNECTING -> "连接中"
            TelephonyManager.DATA_DISCONNECTED -> "已断开"
            TelephonyManager.DATA_SUSPENDED -> "已挂起"
            else -> "未知"
        }
    } catch (e: Exception) {
        "未知"
    }

    private fun dataActivityLabel(manager: TelephonyManager): String? = try {
        when (manager.dataActivity) {
            TelephonyManager.DATA_ACTIVITY_NONE -> "无"
            TelephonyManager.DATA_ACTIVITY_IN -> "接收"
            TelephonyManager.DATA_ACTIVITY_OUT -> "发送"
            TelephonyManager.DATA_ACTIVITY_INOUT -> "收发"
            TelephonyManager.DATA_ACTIVITY_DORMANT -> "休眠"
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun activeCellularNetwork(): NetworkDetails? {
        val cm = connectivityManager ?: return null
        return try {
            val active = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(active) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return null
            val lp = cm.getLinkProperties(active)
            NetworkDetails(
                ipv4 = lp?.let { ipv4Of(it) } ?: emptyList(),
                ipv6 = lp?.let { ipv6Of(it) } ?: emptyList(),
                gateway = lp?.let { gatewayOf(it) },
                dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
                internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun techOrder(tech: CellTech): Int = when (tech) {
        CellTech.NR -> 0
        CellTech.LTE -> 1
        else -> 2
    }

    private fun connectionStatusLabel(cell: CellInfo): String = when (cell.cellConnectionStatus) {
        CellInfo.CONNECTION_PRIMARY_SERVING -> "主服务小区"
        CellInfo.CONNECTION_SECONDARY_SERVING -> "辅服务小区"
        CellInfo.CONNECTION_NONE -> "邻区"
        else -> "未知"
    }

    private fun plmnOf(mcc: CharSequence?, mnc: CharSequence?): String? {
        val m = mcc?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val n = mnc?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        return if (m != null && n != null) "$m$n" else null
    }

    private fun mapCell(cell: CellInfo): CellDetails? = try {
        when (cell) {
            is CellInfoNr -> mapNr(cell)
            is CellInfoLte -> mapLte(cell)
            is CellInfoWcdma -> mapWcdma(cell)
            is CellInfoGsm -> mapGsm(cell)
            is CellInfoTdscdma -> mapTdscdma(cell)
            is CellInfoCdma -> mapCdma(cell)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun mapNr(cell: CellInfoNr): CellDetails? {
        val id = cell.cellIdentity as? CellIdentityNr ?: return null
        val sig = cell.cellSignalStrength as? CellSignalStrengthNr ?: return null
        val arfcn = id.nrarfcn.takeIf { it != CellInfo.UNAVAILABLE }
        val bands = id.bands?.filter { it > 0 }?.joinToString("/") { "n$it" }?.takeIf { it.isNotBlank() }
        val nci = id.nci.takeIf { it != Long.MAX_VALUE && it != CellInfo.UNAVAILABLE_LONG }
        val tac = id.tac.takeIf { it != CellInfo.UNAVAILABLE }
        val ssRsrp = sig.ssRsrp.takeIf { it != CellInfo.UNAVAILABLE }
        val ssRsrq = sig.ssRsrq.takeIf { it != CellInfo.UNAVAILABLE }
        val ssSinr = sig.ssSinr.takeIf { it != CellInfo.UNAVAILABLE }
        if (nci == null && ssRsrp == null && arfcn == null) return null
        val ta = if (Build.VERSION.SDK_INT >= 34) {
            try {
                sig.timingAdvanceMicros.takeIf { it != CellInfo.UNAVAILABLE }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        return CellDetails(
            tech = CellTech.NR,
            registered = cell.isRegistered,
            connectionStatus = connectionStatusLabel(cell),
            band = bands,
            arfcn = arfcn,
            frequencyMhz = arfcn?.let { NrArfcn.toFrequencyMhz(it) },
            bandwidthMhz = null,
            pci = id.pci.takeIf { it != CellInfo.UNAVAILABLE },
            tac = tac,
            cellId = nci?.toString(),
            plmn = plmnOf(id.mccString, id.mncString),
            operator = id.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() },
            ssRsrp = ssRsrp,
            ssRsrq = ssRsrq,
            ssSinr = ssSinr,
            rsrp = null,
            rsrq = null,
            rssnr = null,
            level = sig.level,
            timingAdvance = ta,
        )
    }

    private fun mapLte(cell: CellInfoLte): CellDetails? {
        val id = cell.cellIdentity
        val sig: CellSignalStrengthLte = cell.cellSignalStrength
        val arfcn = id.earfcn.takeIf { it != CellInfo.UNAVAILABLE }
        val bands = id.bands?.filter { it > 0 }?.joinToString("/") { "B$it" }?.takeIf { it.isNotBlank() }
        val ci = id.ci.takeIf { it != CellInfo.UNAVAILABLE }
        val tac = id.tac.takeIf { it != CellInfo.UNAVAILABLE }
        val rsrp = sig.rsrp.takeIf { it != CellInfo.UNAVAILABLE } ?: sig.dbm.takeIf { it != CellInfo.UNAVAILABLE }
        if (ci == null && rsrp == null && arfcn == null) return null
        val bandwidth = id.bandwidth.takeIf { it != CellInfo.UNAVAILABLE }?.let { it / 1000.0 }
        return CellDetails(
            tech = CellTech.LTE,
            registered = cell.isRegistered,
            connectionStatus = connectionStatusLabel(cell),
            band = bands,
            arfcn = arfcn,
            frequencyMhz = null,
            bandwidthMhz = bandwidth,
            pci = id.pci.takeIf { it != CellInfo.UNAVAILABLE },
            tac = tac,
            cellId = ci?.toString(),
            plmn = plmnOf(id.mccString, id.mncString),
            operator = id.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() },
            ssRsrp = null,
            ssRsrq = null,
            ssSinr = null,
            rsrp = rsrp,
            rsrq = sig.rsrq.takeIf { it != CellInfo.UNAVAILABLE },
            rssnr = sig.rssnr.takeIf { it != CellInfo.UNAVAILABLE },
            level = sig.level,
            timingAdvance = sig.timingAdvance.takeIf { it != CellInfo.UNAVAILABLE },
        )
    }

    private fun mapWcdma(cell: CellInfoWcdma): CellDetails? {
        val id: CellIdentityWcdma = cell.cellIdentity
        val sig: CellSignalStrengthWcdma = cell.cellSignalStrength
        val dbm = sig.dbm.takeIf { it != CellInfo.UNAVAILABLE }
        val cid = id.cid.takeIf { it != CellInfo.UNAVAILABLE }
        if (dbm == null && cid == null) return null
        return simpleCell(
            tech = CellTech.WCDMA,
            cell = cell,
            level = sig.level,
            dbm = dbm,
            cc = sig.level,
            plmn = plmnOf(id.mccString, id.mncString),
            operator = id.operatorAlphaLong?.toString(),
            cellId = cid?.toString(),
            tac = id.lac.takeIf { it != CellInfo.UNAVAILABLE },
            pci = id.psc.takeIf { it != CellInfo.UNAVAILABLE },
        )
    }

    private fun mapGsm(cell: CellInfoGsm): CellDetails? {
        val id: CellIdentityGsm = cell.cellIdentity
        val sig: CellSignalStrengthGsm = cell.cellSignalStrength
        val dbm = sig.dbm.takeIf { it != CellInfo.UNAVAILABLE }
        val cid = id.cid.takeIf { it != CellInfo.UNAVAILABLE }
        if (dbm == null && cid == null) return null
        return simpleCell(
            tech = CellTech.GSM,
            cell = cell,
            level = sig.level,
            dbm = dbm,
            cc = sig.level,
            plmn = plmnOf(id.mccString, id.mncString),
            operator = id.operatorAlphaLong?.toString(),
            cellId = cid?.toString(),
            tac = id.lac.takeIf { it != CellInfo.UNAVAILABLE },
            pci = null,
        )
    }

    private fun mapTdscdma(cell: CellInfoTdscdma): CellDetails? {
        val id: CellIdentityTdscdma = cell.cellIdentity
        val sig: CellSignalStrengthTdscdma = cell.cellSignalStrength
        val dbm = sig.dbm.takeIf { it != CellInfo.UNAVAILABLE }
        val cid = id.cid.takeIf { it != CellInfo.UNAVAILABLE }
        if (dbm == null && cid == null) return null
        return simpleCell(
            tech = CellTech.TDSCDMA,
            cell = cell,
            level = sig.level,
            dbm = dbm,
            cc = null,
            plmn = plmnOf(id.mccString, id.mncString),
            operator = id.operatorAlphaLong?.toString(),
            cellId = cid?.toString(),
            tac = id.lac.takeIf { it != CellInfo.UNAVAILABLE },
            pci = null,
        )
    }

    private fun mapCdma(cell: CellInfoCdma): CellDetails? {
        val id: CellIdentityCdma = cell.cellIdentity
        val sig: CellSignalStrengthCdma = cell.cellSignalStrength
        val dbm = sig.dbm.takeIf { it != CellInfo.UNAVAILABLE }
        val bid = id.basestationId.takeIf { it != CellInfo.UNAVAILABLE }
        if (dbm == null && bid == null) return null
        return CellDetails(
            tech = CellTech.CDMA,
            registered = cell.isRegistered,
            connectionStatus = connectionStatusLabel(cell),
            band = null,
            arfcn = null,
            frequencyMhz = null,
            bandwidthMhz = null,
            pci = null,
            tac = id.networkId.takeIf { it != CellInfo.UNAVAILABLE },
            cellId = bid?.toString(),
            plmn = null,
            operator = id.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() },
            ssRsrp = null,
            ssRsrq = null,
            ssSinr = null,
            rsrp = dbm,
            rsrq = null,
            rssnr = null,
            level = sig.level,
            timingAdvance = null,
        )
    }

    private fun simpleCell(
        tech: CellTech,
        cell: CellInfo,
        level: Int,
        dbm: Int?,
        cc: Int?,
        plmn: String?,
        operator: String?,
        cellId: String?,
        tac: Int?,
        pci: Int?,
    ) = CellDetails(
        tech = tech,
        registered = cell.isRegistered,
        connectionStatus = connectionStatusLabel(cell),
        band = null,
        arfcn = cc,
        frequencyMhz = null,
        bandwidthMhz = null,
        pci = pci,
        tac = tac,
        cellId = cellId,
        plmn = plmn,
        operator = operator?.takeIf { it.isNotBlank() },
        ssRsrp = null,
        ssRsrq = null,
        ssSinr = null,
        rsrp = dbm,
        rsrq = null,
        rssnr = null,
        level = level,
        timingAdvance = null,
    )
}
