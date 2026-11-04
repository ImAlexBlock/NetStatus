package com.alexblock.netstatus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NrArfcnTest {

    @Test
    fun lowRange() {
        assertEquals(0.0, NrArfcn.toFrequencyMhz(0)!!, 1e-9)
        assertEquals(100.0, NrArfcn.toFrequencyMhz(20000)!!, 1e-9)
    }

    @Test
    fun midRange() {
        assertEquals(3000.0, NrArfcn.toFrequencyMhz(600000)!!, 1e-9)
        assertEquals(3549.99, NrArfcn.toFrequencyMhz(636666)!!, 1e-6)
    }

    @Test
    fun highRange() {
        assertEquals(24250.0, NrArfcn.toFrequencyMhz(2016667)!!, 1e-6)
        assertEquals(99999.88, NrArfcn.toFrequencyMhz(3279165)!!, 0.01)
    }

    @Test
    fun outOfRange() {
        assertNull(NrArfcn.toFrequencyMhz(-1))
        assertNull(NrArfcn.toFrequencyMhz(3279166))
    }
}
