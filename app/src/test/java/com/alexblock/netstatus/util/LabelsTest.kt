package com.alexblock.netstatus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {

    @Test
    fun standards() {
        assertEquals("未知", standardLabel(WifiStandard.UNKNOWN))
        assertEquals("802.11a/b/g", standardLabel(WifiStandard.LEGACY))
        assertEquals("Wi-Fi 4 (802.11n)", standardLabel(WifiStandard.N))
        assertEquals("Wi-Fi 5 (802.11ac)", standardLabel(WifiStandard.AC))
        assertEquals("Wi-Fi 6/6E (802.11ax)", standardLabel(WifiStandard.AX))
        assertEquals("Wi-Fi 7 (802.11be)", standardLabel(WifiStandard.BE))
    }

    @Test
    fun security() {
        assertEquals("开放", securityLabel(WifiSecurity.OPEN))
        assertEquals("WPA2-PSK", securityLabel(WifiSecurity.PSK))
        assertEquals("WPA3-SAE", securityLabel(WifiSecurity.SAE))
        assertEquals("WPA3-企业 192 位", securityLabel(WifiSecurity.EAP_WPA3_192))
        assertEquals("DPP", securityLabel(WifiSecurity.DPP))
    }

    @Test
    fun qualityAndLevel() {
        assertEquals("极佳", rssiQuality(-15))
        assertEquals("良好", rssiQuality(-55))
        assertEquals("一般", rssiQuality(-65))
        assertEquals("较差", rssiQuality(-75))
        assertEquals("很差", rssiQuality(-90))

        assertEquals(4, rssiLevel(-40))
        assertEquals(3, rssiLevel(-55))
        assertEquals(2, rssiLevel(-65))
        assertEquals(1, rssiLevel(-75))
        assertEquals(0, rssiLevel(-95))

        assertEquals("极佳", levelLabel(4))
        assertEquals("无信号", levelLabel(0))
    }

    @Test
    fun networkTypes() {
        assertEquals("5G NR", networkTypeLabel(20))
        assertEquals("4G LTE", networkTypeLabel(13))
        assertEquals("3G WCDMA", networkTypeLabel(10))
        assertEquals("2G GSM", networkTypeLabel(16))
        assertEquals("Wi-Fi 通话", networkTypeLabel(18))
        assertEquals("未知", networkTypeLabel(0))
    }

    @Test
    fun formatting() {
        assertEquals("刚刚", formatAgo(1_000_000L, 997_000L))
        assertEquals("12 秒前", formatAgo(1_000_000L, 988_000L))
        assertEquals("3 分钟前", formatAgo(1_000_000L, 820_000L))
        assertEquals("2 小时前", formatAgo(10_000_000L, 2_800_000L))
        assertEquals("—", formatMbps(null))
        assertEquals("172 Mbps", formatMbps(172))
        assertEquals("2412 MHz", formatFrequency(2412))
        assertEquals("—", formatMhzDouble(null))
        assertEquals("3549.99 MHz", formatMhzDouble(3549.99))
    }
}
