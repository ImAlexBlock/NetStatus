package com.alexblock.netstatus.util

enum class WifiBand(val label: String) {
    BAND_2G4("2.4 GHz"),
    BAND_5G("5 GHz"),
    BAND_6G("6 GHz"),
    OTHER("其他"),
}

object WifiBands {

    fun bandOf(freqMhz: Int): WifiBand = when (freqMhz) {
        in 2400..2499 -> WifiBand.BAND_2G4
        in 4900..5924 -> WifiBand.BAND_5G
        in 5925..7125 -> WifiBand.BAND_6G
        else -> WifiBand.OTHER
    }

    fun channelOf(freqMhz: Int): Int? = when (bandOf(freqMhz)) {
        WifiBand.BAND_2G4 -> if (freqMhz == 2484) 14 else (freqMhz - 2407) / 5
        WifiBand.BAND_5G -> (freqMhz - 5000) / 5
        WifiBand.BAND_6G -> (freqMhz - 5950) / 5
        WifiBand.OTHER -> null
    }

    fun widthLabel(width: Int): String = when (width) {
        0 -> "20 MHz"
        1 -> "40 MHz"
        2 -> "80 MHz"
        3 -> "160 MHz"
        4 -> "80+80 MHz"
        5 -> "320 MHz"
        else -> "未知"
    }
}
