package com.partyos.tv.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.theme.Party

@Composable
fun PerfHud(monitor: PerfMonitor, modifier: Modifier = Modifier) {
    val s by monitor.live.collectAsState()
    val color = if (s.onTimePct >= 95) Party.Mint else if (s.onTimePct >= 85) Party.Gold else Party.Red
    Text(
        "${s.fps} fps · ${"%.0f".format(s.onTimePct)}% on time",
        style = MaterialTheme.typography.labelLarge, color = color,
        modifier = modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
