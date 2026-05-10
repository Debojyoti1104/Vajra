package com.aegismesh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegismesh.core.service.RadioState

// technical status indicator in the corner
@Composable
fun MeshGuardian(
    radioState: RadioState,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusText) = when (radioState) {
        RadioState.IDLE -> Color.Gray to "STANDBY"
        RadioState.SCANNING_BALANCED -> Color.Cyan to "SCANNING"
        RadioState.SCANNING_LOW_LATENCY -> Color(0xFF00FFCC) to "CRITICAL"
        RadioState.RELAYING -> Color(0xFFFF9900) to "RELAYING"
        RadioState.ERROR -> Color.Red to "ERROR"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(8.dp)
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}
