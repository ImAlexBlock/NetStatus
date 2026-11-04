package com.alexblock.netstatus.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.alexblock.netstatus.data.NetworkDetails
import com.alexblock.netstatus.util.WifiBands
import com.alexblock.netstatus.util.REQUIRED_WIFI
import com.alexblock.netstatus.util.hasAll
import com.alexblock.netstatus.util.locationEnabled
import com.alexblock.netstatus.util.securityLabel
import com.alexblock.netstatus.util.wifiSecurityOf
import com.alexblock.netstatus.util.wifiStandardOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private const val UNKNOWN_SSID_LABEL = "未知网络"
private const val REDACTED_BSSID = "02:00:00:00:00:00"
private const val HIDDEN_SSID_LABEL = "(隐藏网络)"

class WifiRepository(private val context: Context) {

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(
        WifiSnapshot(
            wifiEnabled = false,
            connection = null,
            network = null,
            accessPoints = emptyList(),
            lastScanAtMillis = null,
            scanning = false,
            scanThrottled = false,
            locationEnabled = false,
            permissionsGranted = false,
            error = null,
        ),
    )
    val state: StateFlow<WifiSnapshot> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var started = false
    private var connectedBssid: String? = null

    private data class NetEntry(
        val caps: NetworkCapabilities? = null,
        val lp: LinkProperties? = null,
    )

    private val netEntries = mutableMapOf<Network, NetEntry>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifiEntry = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            if (!wifiEntry) {
                publishNetworkDetails()
                return
            }
            synchronized(netEntries) {
                val previous = netEntries[network] ?: NetEntry()
                netEntries[network] = previous.copy(caps = caps)
            }
            publishNetworkDetails()
            // capabilities.transportInfo is redacted; re-read WifiManager.connectionInfo off-thread.
            scope.launch { refreshScanResultsOnly() }
        }

        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            synchronized(netEntries) {
                val previous = netEntries[network] ?: NetEntry()
                netEntries[network] = previous.copy(lp = lp)
            }
            publishNetworkDetails()
        }

        override fun onLost(network: Network) {
            synchronized(netEntries) { netEntries.remove(network) }
            val wifi = currentWifiEntry()
            if (wifi == null) {
                connectedBssid = null
                _state.update { it.copy(connection = null, network = null) }
            } else {
                publishNetworkDetails()
                scope.launch { refreshScanResultsOnly() }
            }
        }
    }

    private fun currentWifiEntry(): NetEntry? = synchronized(netEntries) {
        netEntries.values.lastOrNull { it.caps != null }
    }

    private fun publishNetworkDetails() {
        val entry = currentWifiEntry() ?: activeWifiNetworkFallback()
        val caps = entry?.caps
        val lp = entry?.lp
        _state.update { current ->
            current.copy(
                network = if (caps == null && lp == null) null else buildDetails(caps, lp),
            )
        }
    }

    private fun activeWifiNetworkFallback(): NetEntry? = try {
        val cm = connectivityManager
        val active = cm?.activeNetwork
        val caps = if (active == null) null else cm?.getNetworkCapabilities(active)
        if (active != null &&
            caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        ) {
            NetEntry(caps = caps, lp = cm?.getLinkProperties(active))
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }


    private var scanCallback: WifiManager.ScanResultsCallback? = null

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val enabled = intent?.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1) ==
                WifiManager.WIFI_STATE_ENABLED
            _state.update { it.copy(wifiEnabled = enabled) }
        }
    }

    fun hasPermissions(): Boolean = context.hasAll(REQUIRED_WIFI)

    fun start() {
        if (started) return
        started = true

        val enabled = try {
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            false
        }

        if (!hasPermissions()) {
            _state.update {
                it.copy(
                    permissionsGranted = false,
                    wifiEnabled = enabled,
                    locationEnabled = context.locationEnabled(),
                )
            }
            return
        }

        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            _state.update { it.copy(error = "网络回调注册失败") }
        }

        try {
            val cb = object : WifiManager.ScanResultsCallback() {
                override fun onScanResultsAvailable() {
                    _state.update {
                        it.copy(
                            lastScanAtMillis = System.currentTimeMillis(),
                            scanning = false,
                        )
                    }
                    scope.launch { refreshScanResultsOnly() }
                }
            }
            scanCallback = cb
            wifiManager?.registerScanResultsCallback(context.mainExecutor, cb)
        } catch (e: Exception) {
            _state.update { it.copy(error = "扫描回调注册失败") }
        }

        try {
            context.registerReceiver(
                wifiStateReceiver,
                IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } catch (e: Exception) {
            // Non-fatal.
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore.
        }
        try {
            scanCallback?.let { wifiManager?.unregisterScanResultsCallback(it) }
        } catch (e: Exception) {
            // Ignore.
        }
        scanCallback = null
        try {
            context.unregisterReceiver(wifiStateReceiver)
        } catch (e: Exception) {
            // Ignore.
        }
    }

    fun scan(): Boolean {
        if (!hasPermissions()) return false
        val manager = wifiManager ?: return false
        _state.update { it.copy(scanning = true, scanThrottled = false) }
        return try {
            @Suppress("DEPRECATION")
            val started = manager.startScan()
            if (!started) {
                _state.update { it.copy(scanning = false, scanThrottled = true) }
            }
            started
        } catch (e: SecurityException) {
            _state.update {
                it.copy(scanning = false, error = "缺少精确位置权限，无法扫描")
            }
            false
        } catch (e: Exception) {
            _state.update { it.copy(scanning = false, error = "扫描失败") }
            false
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val manager = wifiManager
        val enabled = try {
            manager?.isWifiEnabled == true
        } catch (e: Exception) {
            false
        }
        val granted = hasPermissions()
        val locEnabled = context.locationEnabled()

        if (!granted || manager == null) {
            _state.update {
                it.copy(
                    wifiEnabled = enabled,
                    permissionsGranted = false,
                    locationEnabled = locEnabled,
                )
            }
            return@withContext
        }



        val aps = readScanResults(manager, connectedBssid)
        publishNetworkDetails()

        _state.update { current ->
            current.copy(
                wifiEnabled = enabled,
                permissionsGranted = true,
                locationEnabled = locEnabled,
                connection = resolveConnection(aps),
                accessPoints = aps,
                error = null,
            )
        }
    }

    private suspend fun refreshScanResultsOnly() = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext
        val manager = wifiManager ?: return@withContext
        val aps = readScanResults(manager, connectedBssid)
        if (aps.isEmpty()) return@withContext
        _state.update { current ->
            current.copy(
                accessPoints = aps,
                connection = resolveConnection(aps),
            )
        }
    }

    /** Authorized source of truth: WifiManager.connectionInfo. transportInfo is redacted. */
    private fun resolveConnection(aps: List<WifiAp>): WifiConnection? {
        val live = wifiManager?.let { legacyConnection(it) }
        connectedBssid = live?.bssid
            ?: _state.value.connection?.bssid?.takeIf { it != REDACTED_BSSID }
        val base = live ?: _state.value.connection
        return mergeWithScan(base, aps)?.let { mergeSsid(it, aps) }
    }

    private fun mergeSsid(conn: WifiConnection, aps: List<WifiAp>): WifiConnection {
        if (conn.ssid != UNKNOWN_SSID_LABEL) return conn
        if (conn.bssid == REDACTED_BSSID) return conn
        val fromScan = aps.firstOrNull {
            it.bssid.equals(conn.bssid, ignoreCase = true) && it.ssid != HIDDEN_SSID_LABEL
        }?.ssid
        val fromState = _state.value.connection
            ?.takeIf { it.ssid != UNKNOWN_SSID_LABEL && it.bssid.equals(conn.bssid, ignoreCase = true) }
            ?.ssid
        val ssid = fromScan ?: fromState ?: conn.ssid
        return conn.copy(ssid = ssid)
    }

    private fun mergeWithScan(conn: WifiConnection?, aps: List<WifiAp>): WifiConnection? {
        if (conn == null) return null
        if (conn.channelWidth != null) return conn
        val match = aps.firstOrNull { it.bssid.equals(conn.bssid, ignoreCase = true) }
        return if (match?.width != null) conn.copy(channelWidth = match.width) else conn
    }

    private fun readScanResults(manager: WifiManager, connectedBssid: String?): List<WifiAp> {
        val results: List<ScanResult> = try {
            manager.scanResults
        } catch (e: SecurityException) {
            _state.update { it.copy(error = "缺少精确位置权限，无法读取扫描结果") }
            return _state.value.accessPoints
        } catch (e: Exception) {
            _state.update { it.copy(error = "读取扫描结果失败") }
            return _state.value.accessPoints
        }

        return results.map { mapAp(it, connectedBssid) }
            .sortedWith(compareByDescending<WifiAp> { it.connected }.thenByDescending { it.rssi })
    }

    private fun mapAp(result: ScanResult, connectedBssid: String?): WifiAp {
        val ssid = result.wifiSsid?.toString()?.trim('"')?.takeIf {
            it.isNotBlank() && it != WifiManager.UNKNOWN_SSID
        } ?: HIDDEN_SSID_LABEL
        val security = try {
            result.securityTypes
                .map { securityLabel(wifiSecurityOf(it)) }
                .distinct()
                .joinToString(" / ")
                .ifBlank { "未知" }
        } catch (e: Exception) {
            "未知"
        }
        return WifiAp(
            ssid = ssid,
            bssid = result.BSSID ?: "—",
            rssi = result.level,
            frequencyMhz = result.frequency,
            channel = WifiBands.channelOf(result.frequency),
            band = WifiBands.bandOf(result.frequency),
            width = result.channelWidth,
            standard = wifiStandardOf(result.wifiStandard),
            security = security,
            connected = connectedBssid != null && result.BSSID.equals(connectedBssid, ignoreCase = true),
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyConnection(manager: WifiManager): WifiConnection? = try {
        val info = manager.connectionInfo
        if (info == null || ssidOf(info) == UNKNOWN_SSID_LABEL) {
            null
        } else {
            buildConnection(info)
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }

    private fun ssidOf(info: WifiInfo): String {
        val raw = info.ssid?.trim('"')
        return if (raw.isNullOrBlank() || raw == WifiManager.UNKNOWN_SSID) UNKNOWN_SSID_LABEL else raw
    }

    private fun buildConnection(info: WifiInfo): WifiConnection {
        val ssid = ssidOf(info)
        val tx = info.linkSpeed.takeIf { it > 0 }
        return WifiConnection(
            ssid = ssid,
            bssid = info.bssid ?: "—",
            rssi = info.rssi,
            frequencyMhz = info.frequency,
            standard = wifiStandardOf(info.wifiStandard),
            security = wifiSecurityOf(info.currentSecurityType),
            txMbps = tx,
            rxMbps = info.rxLinkSpeedMbps.takeIf { it > 0 },
            maxTxMbps = info.maxSupportedTxLinkSpeedMbps.takeIf { it > 0 },
            maxRxMbps = info.maxSupportedRxLinkSpeedMbps.takeIf { it > 0 },
            channelWidth = null,
            supplicantState = info.supplicantState?.name,
            hiddenSsid = info.hiddenSSID,
        )
    }

    private fun buildDetails(caps: NetworkCapabilities?, lp: LinkProperties?): NetworkDetails? {
        if (caps == null && lp == null) return null
        return NetworkDetails(
            ipv4 = lp?.let { ipv4Of(it) } ?: emptyList(),
            ipv6 = lp?.let { ipv6Of(it) } ?: emptyList(),
            gateway = lp?.let { gatewayOf(it) },
            dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            captivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
        )
    }

    fun bandSupport(): Map<String, Boolean> {
        val manager = wifiManager ?: return emptyMap()
        return try {
            mapOf(
                "2.4 GHz" to manager.is24GHzBandSupported,
                "5 GHz" to manager.is5GHzBandSupported,
                "6 GHz" to (Build.VERSION.SDK_INT >= 33 && manager.is6GHzBandSupported),
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

internal fun ipv4Of(lp: LinkProperties): List<String> = lp.linkAddresses.mapNotNull { la ->
    val addr = la.address
    if (addr is java.net.Inet4Address) "${addr.hostAddress}/${la.prefixLength}" else null
}

internal fun ipv6Of(lp: LinkProperties): List<String> = lp.linkAddresses.mapNotNull { la ->
    val addr = la.address
    if (addr is java.net.Inet6Address) "${addr.hostAddress}/${la.prefixLength}" else null
}

internal fun gatewayOf(lp: LinkProperties): String? {
    val route = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is java.net.Inet4Address }
        ?: lp.routes.firstOrNull { it.isDefaultRoute }
    return route?.gateway?.hostAddress
}
