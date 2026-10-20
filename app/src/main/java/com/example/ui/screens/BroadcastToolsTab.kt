package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.CyberCardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.BotHostViewModel

@Composable
fun BroadcastToolsTab(
    viewModel: BotHostViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.allUsers.collectAsState()
    val pingLatency by viewModel.pingLatencyMs.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()

    var broadcastText by remember { mutableStateOf("") }
    var isBroadcasting by remember { mutableStateOf(false) }
    var broadcastResult by remember { mutableStateOf<String?>(null) }
    var isBotLocked by remember { mutableStateOf(viewModel.preferences.isBotLocked) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Broadcast Section ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("broadcast_card"),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcast Announcement", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Text(
                    text = "Transmit a message to all ${users.size} registered users across Telegram.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = broadcastText,
                    onValueChange = { broadcastText = it },
                    label = { Text("Broadcast Message (Markdown supported)") },
                    placeholder = { Text("📢 Hello users! New update has been deployed...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("broadcast_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recipients: ${users.size}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            if (broadcastText.isNotBlank()) {
                                isBroadcasting = true
                                broadcastResult = null
                                viewModel.sendBroadcast(broadcastText) { sent, failed ->
                                    isBroadcasting = false
                                    broadcastResult = "✅ Sent to $sent users (${failed} failed)"
                                    broadcastText = ""
                                }
                            }
                        },
                        enabled = broadcastText.isNotBlank() && !isBroadcasting,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.testTag("send_broadcast_btn")
                    ) {
                        if (isBroadcasting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sending...", color = Color.Black)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Broadcast", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                AnimatedVisibility(visible = broadcastResult != null) {
                    broadcastResult?.let { res ->
                        Text(
                            text = res,
                            color = OnlineGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // --- Bot Lock Control ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBotLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isBotLocked) OfflineRed else OnlineGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBotLocked) "Bot is Locked 🔒" else "Bot is Open 🔓",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isBotLocked) "Only Admins & Owner can interact" else "Open to public users",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = isBotLocked,
                    onCheckedChange = {
                        isBotLocked = it
                        viewModel.toggleBotLock()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OfflineRed,
                        checkedTrackColor = OfflineRed.copy(alpha = 0.3f),
                        uncheckedThumbColor = OnlineGreen,
                        uncheckedTrackColor = OnlineGreen.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // --- Fast Operations (Run All Scripts + Speed Ping) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Operations & Diagnostics", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.runAllScripts() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run All Bots", color = OnlineGreen, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.testConnection() },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardElevated),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberCyan, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speed Ping", color = CyberCyan, fontSize = 12.sp)
                        }
                    }
                }

                if (pingLatency != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Telegram API Latency: ${pingLatency}ms",
                        color = CyberCyan,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
