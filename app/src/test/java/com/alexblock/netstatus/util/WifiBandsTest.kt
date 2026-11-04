package com.alexblock.netstatus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiBandsTest {

    @Test
    fun bands() {
        assertEquals(WifiBand.BAND_2G4, WifiBands.bandOf(2412))
        assertEquals(WifiBand.BAND_2G4, WifiBands.bandOf(2462))
        assertEquals(WifiBand.BAND_5G, WifiBands.bandOf(5180))
        assertEquals(WifiBand.BAND_5G, WifiBands.bandOf(5745))
        assertEquals(WifiBand.BAND_6G, WifiBands.bandOf(5955))
        assertEquals(WifiBand.BAND_6G, WifiBands.bandOf(6135))
        assertEquals(WifiBand.OTHER, WifiBands.bandOf(1000))
    }

    @Test
    fun channels() {
        assertEquals(1, WifiBands.channelOf(2412))
        assertEquals(6, WifiBands.channelOf(2437))
        assertEquals(11, WifiBands.channelOf(2462))
        assertEquals(14, WifiBands.channelOf(2484))
        assertEquals(36, WifiBands.channelOf(5180))
        assertEquals(149, WifiBands.channelOf(5745))
        assertEquals(1, WifiBands.channelOf(5955))
        assertEquals(33, WifiBands.channelOf(6115))
        assertEquals(null, WifiBands.channelOf(1000))
    }

    @Test
    fun widths() {
        assertEquals("20 MHz", WifiBands.widthLabel(0))
        assertEquals("40 MHz", WifiBands.widthLabel(1))
        assertEquals("80 MHz", WifiBands.widthLabel(2))
        assertEquals("160 MHz", WifiBands.widthLabel(3))
        assertEquals("80+80 MHz", WifiBands.widthLabel(4))
        assertEquals("320 MHz", WifiBands.widthLabel(5))
        assertEquals("未知", WifiBands.widthLabel(99))
    }
}
