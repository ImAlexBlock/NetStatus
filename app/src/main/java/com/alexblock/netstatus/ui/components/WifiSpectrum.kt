package com.alexblock.netstatus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexblock.netstatus.data.wifi.WifiAp
import com.alexblock.netstatus.util.WifiBand
import kotlin.math.exp

/** Frequency span drawn for one band. */
private fun bandRange(band: WifiBand): ClosedFloatingPointRange<Float> = when (band) {
    WifiBand.BAND_2G4 -> 2392f..2492f
    WifiBand.BAND_5G -> 5140f..5895f
    WifiBand.BAND_6G -> 5925f..7125f
    WifiBand.OTHER -> 0f..0f
}

private fun widthMhz(width: Int?): Float = when (width) {
    1 -> 40f
    2 -> 80f
    3 -> 160f
    4 -> 160f
    5 -> 320f
    else -> 20f
}

@Composable
fun WifiSpectrum(
    accessPoints: List<WifiAp>,
    modifier: Modifier = Modifier,
) {
    val bands = listOf(WifiBand.BAND_2G4, WifiBand.BAND_5G, WifiBand.BAND_6G)
    val colors = mapOf(
        WifiBand.BAND_2G4 to MaterialTheme.colorScheme.primary,
        WifiBand.BAND_5G to MaterialTheme.colorScheme.tertiary,
        WifiBand.BAND_6G to MaterialTheme.colorScheme.secondary,
    )
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Column(modifier.fillMaxWidth()) {
        bands.forEach { band ->
            val aps = accessPoints.filter { it.band == band }
            if (aps.isNotEmpty()) {
                SpectrumRow(
                    band = band,
                    aps = aps,
                    color = colors[band] ?: MaterialTheme.colorScheme.primary,
                    labelColor = labelColor,
                    textMeasurer = textMeasurer,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        Text(
            text = "标注为信道号；纵轴 -100…-30 dBm，横向宽度为信道带宽，粗线为当前连接",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpectrumRow(
    band: WifiBand,
    aps: List<WifiAp>,
    color: Color,
    labelColor: Color,
    textMeasurer: TextMeasurer,
) {
    val range = bandRange(band)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = band.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${aps.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(top = 14.dp),
        ) {
            drawSpectrum(range, aps, color, gridColor, labelColor, textMeasurer)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = "${range.start.toInt()} MHz",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${range.endInclusive.toInt()} MHz",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}

private fun DrawScope.drawSpectrum(
    range: ClosedFloatingPointRange<Float>,
    aps: List<WifiAp>,
    color: Color,
    gridColor: Color,
    labelColor: Color,
    textMeasurer: TextMeasurer,
) {
    val left = 0f
    val right = size.width
    val top = 0f
    val bottom = size.height - 2.dp.toPx()
    val span = (range.endInclusive - range.start).coerceAtLeast(1f)
    // Headroom keeps the strongest peak clear of the top edge so its label always fits.
    val usable = (bottom - top) * 0.88f

    listOf(-50, -70, -90).forEach { dbm ->
        val y = bottom - amplitudeOf(dbm) * usable
        drawLine(
            color = gridColor,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = 1.dp.toPx() / 2f,
        )
    }

    val samples = 160
    aps.sortedBy { it.rssi }.forEach { ap ->
        val center = ap.frequencyMhz.toFloat()
        val sigma = widthMhz(ap.width) / 2.6f
        val peak = amplitudeOf(ap.rssi)
        val path = Path()
        var started = false
        for (i in 0..samples) {
            val freq = range.start + span * i / samples
            val delta = (freq - center) / sigma
            val value = peak * exp(-0.5f * delta * delta)
            val x = left + right * i / samples
            val y = bottom - value * usable
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        val stroke = if (ap.connected) 2.dp.toPx() else 1.4f.dp.toPx()
        val alpha = if (ap.connected) 1f else 0.5f
        drawPath(path = path, color = color.copy(alpha = alpha), style = Stroke(width = stroke))
    }

    // One channel label per distinct channel, at its strongest curve's peak.
    val style = TextStyle(
        color = labelColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )
    val minGap = 26.dp.toPx()
    val rowLastX = floatArrayOf(-1e6f, -1e6f)
    aps.filter { it.channel != null }
        .groupBy { it.channel }
        .mapNotNull { (channel, group) -> channel?.let { it to group.maxByOrNull { ap -> ap.rssi }!! } }
        .sortedBy { (_, ap) -> ap.frequencyMhz }
        .forEach { (channel, ap) ->
            val x = left + right * ((ap.frequencyMhz - range.start) / span)
            val row = when {
                x - rowLastX[0] >= minGap -> 0
                x - rowLastX[1] >= minGap -> 1
                else -> return@forEach
            }
            val label = textMeasurer.measure(channel.toString(), style)
            val gap = 2.dp.toPx()
            val peak = amplitudeOf(ap.rssi)
            val peakY = bottom - peak * usable
            val y = peakY - label.size.height - gap - row * (label.size.height + gap)
            if (y < top) return@forEach
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = (x - label.size.width / 2f).coerceIn(left, right - label.size.width),
                    y = y,
                ),
            )
            rowLastX[row] = x
        }
}

/** Maps dBm to 0..1 height over a -100..-30 dBm window. */
private fun amplitudeOf(dbm: Int): Float = ((dbm + 100f) / 70f).coerceIn(0f, 1f)
