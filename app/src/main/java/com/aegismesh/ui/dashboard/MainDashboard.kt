package com.aegismesh.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.aegismesh.R
import com.aegismesh.core.models.MeshPacket
import com.aegismesh.core.service.RadioState
import com.aegismesh.ui.components.MeshGuardian

@Composable
fun MainDashboard(
    radioState: RadioState,
    incomingPackets: List<Pair<MeshPacket, String>>, // Packet and Decompressed Intent
    availableIntents: List<String>,
    selectedPacket: MeshPacket?,
    beepEnabled: Boolean,
    onBeepToggle: (Boolean) -> Unit,
    onPacketSelected: (MeshPacket?) -> Unit,
    onHoldSos: (String) -> Unit
) {
    var selectedIntent by remember { mutableStateOf("I'm trapped and bleeding") }
    var showDropdown by remember { mutableStateOf(false) }

    // Dark Mode Protocol: Pure Black Background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // If a packet is selected, show the map overlay
        if (selectedPacket != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AegisMapView(packet = selectedPacket)
                
                // Overlay Controls
                Column(modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
                    Button(onClick = { onPacketSelected(null) }) {
                        Text("BACK TO FEED")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onBeepToggle(!beepEnabled) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (beepEnabled) Color.Green else Color.Gray
                        )
                    ) {
                        Text(if (beepEnabled) "BEEP: ON" else "BEEP: OFF")
                    }
                }
            }
            return@Column
        }

        // Header with Mesh Guardian
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VAJRA",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            MeshGuardian(radioState = radioState)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Central Reactive Element: Pulse Animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            PulseAnimation(isActive = radioState != RadioState.IDLE)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Intent Selector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            OutlinedButton(
                onClick = { showDropdown = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Cyan),
                border = BorderStroke(1.dp, Color.Cyan)
            ) {
                Text(text = "INTENT: ${selectedIntent.uppercase()}")
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(Color(0xFF222222))
            ) {
                availableIntents.forEach { intent ->
                    DropdownMenuItem(
                        text = { Text(intent, color = Color.White) },
                        onClick = {
                            selectedIntent = intent
                            showDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SOS Button
        SosButton(onHoldSos = { onHoldSos(selectedIntent) })

        Spacer(modifier = Modifier.height(32.dp))

        // Live Feed Header
        Text(
            text = "LIVE INTERCEPTS",
            color = Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scrolling Live Feed
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(incomingPackets) { (packet, text) ->
                InterceptCard(
                    packet = packet, 
                    intentText = text,
                    onClick = { onPacketSelected(packet) }
                )
            }
        }
    }
}

@Composable
fun PulseAnimation(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActive) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.size(200.dp)) {
        val radius = size.minDimension / 2
        if (isActive) {
            // Inner Core
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.5f),
                radius = radius * 0.2f
            )
            // Expanding Radar Ring
            drawCircle(
                color = Color.Cyan.copy(alpha = 1f - pulseRatio),
                radius = radius * pulseRatio
            )
            // Secondary Ring
            val secondaryRatio = (pulseRatio + 0.5f) % 1f
            drawCircle(
                color = Color.Cyan.copy(alpha = 1f - secondaryRatio),
                radius = radius * secondaryRatio
            )
        } else {
            drawCircle(
                color = Color.DarkGray,
                radius = radius * 0.2f
            )
        }
    }
}

@Composable
fun SosButton(onHoldSos: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(if (isHolding) Color(0xFFAA0000) else Color(0xFFFF0000))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            val success = tryAwaitRelease()
                            isHolding = false
                        },
                        onLongPress = {
                            onHoldSos()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isHolding) "HOLD..." else "SOS",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun InterceptCard(
    packet: MeshPacket,
    intentText: String,
    onClick: () -> Unit
) {
    // Estimating distance purely for UI demo, in reality driven by RSSI
    val mockDistance = "${(packet.hopCount * 15) + (Math.random() * 10).toInt()}m"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111111)) // Slightly elevated from pure black
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = intentText,
                color = Color(0xFFFF3333),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ID: ${packet.messageId.toUInt().toString(16).uppercase()} • Hops Left: ${packet.hopCount}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = mockDistance,
                color = Color.Cyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "Just now",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
        }
    }
}
