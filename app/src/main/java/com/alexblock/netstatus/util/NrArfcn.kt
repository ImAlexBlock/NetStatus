package com.alexblock.netstatus.util

/** NR-ARFCN to frequency mapping, 3GPP TS 38.104 Table 5.4.2.1-1. */
object NrArfcn {

    fun toFrequencyMhz(nrarfcn: Int): Double? = when {
        nrarfcn < 0 -> null
        nrarfcn < 600000 -> nrarfcn * 0.005
        nrarfcn < 2016667 -> 3000.0 + (nrarfcn - 600000) * 0.015
        nrarfcn <= 3279165 -> 24250.0 + (nrarfcn - 2016667) * 0.060
        else -> null
    }
}
