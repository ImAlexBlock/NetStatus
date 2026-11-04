package com.alexblock.netstatus.ui.wwan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexblock.netstatus.data.DiagnosticCheck
import com.alexblock.netstatus.data.ProbeResult
import com.alexblock.netstatus.data.network.ConnectivityDiagnostics
import com.alexblock.netstatus.data.telephony.TelephonyRepository
import com.alexblock.netstatus.data.telephony.WwanSnapshot
import com.alexblock.netstatus.diagnostics.WwanDiagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WwanUiState(
    val snapshot: WwanSnapshot,
    val probe: ProbeResult?,
    val checks: List<DiagnosticCheck>,
    val refreshing: Boolean,
)

class WwanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelephonyRepository(application)

    private val _state = MutableStateFlow(
        WwanUiState(
            snapshot = repository.state.value,
            probe = null,
            checks = emptyList(),
            refreshing = false,
        ),
    )
    val state: StateFlow<WwanUiState> = _state.asStateFlow()

    private var guard: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            repository.state.collect { snapshot ->
                _state.update { current ->
                    current.copy(
                        snapshot = snapshot,
                        checks = WwanDiagnostics.evaluate(snapshot, current.probe),
                    )
                }
            }
        }
    }

    fun start() {
        repository.start()
        refresh()
    }

    fun stop() {
        repository.stop()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            guard?.cancel()
            guard = viewModelScope.launch {
                delay(5000)
                _state.update { it.copy(refreshing = false) }
            }
            repository.refresh()
            val snapshot = repository.state.value
            val probe = if (snapshot.network?.internet == true) {
                ConnectivityDiagnostics.probe()
            } else {
                null
            }
            guard?.cancel()
            _state.update {
                it.copy(
                    probe = probe,
                    checks = WwanDiagnostics.evaluate(repository.state.value, probe),
                    refreshing = false,
                )
            }
        }
    }
}
