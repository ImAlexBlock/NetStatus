package com.alexblock.netstatus.ui.wlan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexblock.netstatus.data.DiagnosticCheck
import com.alexblock.netstatus.data.ProbeResult
import com.alexblock.netstatus.data.network.ConnectivityDiagnostics
import com.alexblock.netstatus.data.wifi.WifiRepository
import com.alexblock.netstatus.data.wifi.WifiSnapshot
import com.alexblock.netstatus.diagnostics.WlanDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WlanUiState(
    val snapshot: WifiSnapshot,
    val probe: ProbeResult?,
    val checks: List<DiagnosticCheck>,
    val refreshing: Boolean,
    val bandSupport: Map<String, Boolean>,
)

class WlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiRepository(application)

    private val _state = MutableStateFlow(
        WlanUiState(
            snapshot = repository.state.value,
            probe = null,
            checks = emptyList(),
            refreshing = false,
            bandSupport = repository.bandSupport(),
        ),
    )
    val state: StateFlow<WlanUiState> = _state.asStateFlow()

    private var scanGuard: Job? = null

    init {
        viewModelScope.launch {
            repository.state.collect { snapshot ->
                _state.update { current ->
                    current.copy(
                        snapshot = snapshot,
                        checks = WlanDiagnostics.evaluate(snapshot, current.probe),
                    )
                }
            }
        }
    }

    fun start() {
        repository.start()
        refresh()
        if (repository.state.value.lastScanAtMillis == null) scan()
    }

    fun stop() {
        repository.stop()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            repository.refresh()
            val snapshot = repository.state.value
            val probe = if (snapshot.connection != null && snapshot.network?.internet == true) {
                ConnectivityDiagnostics.probe()
            } else {
                null
            }
            _state.update {
                it.copy(
                    probe = probe,
                    checks = WlanDiagnostics.evaluate(snapshot, probe),
                    refreshing = false,
                )
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.scan() }
            scanGuard?.cancel()
            scanGuard = viewModelScope.launch {
                delay(6000)
                _state.update { current ->
                    val snapshot = repository.state.value
                    current.copy(
                        snapshot = if (snapshot.scanning) snapshot.copy(scanning = false) else snapshot,
                        refreshing = false,
                    )
                }
            }
            repository.refresh()
        }
    }
}
