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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MetricStatCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.CyberCardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberNeonBlue
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.BotHostViewModel

@Composable
fun DashboardTab(
    viewModel: BotHostViewModel,
    modifier: Modifier = Modifier
) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val botUsername by viewModel.botUsername.collectAsState()
    val botFirstName by viewModel.botFirstName.collectAsState()
    val uptime by viewModel.uptimeString.collectAsState()
    val processedUpdates by viewModel.processedUpdates.collectAsState()
    val pingLatency by viewModel.pingLatencyMs.collectAsState()
    val connectionResult by viewModel.connectionTestResult.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val files by viewModel.allFiles.collectAsState()
    val runningCount = files.count { it.status == "RUNNING" }
    val totalUsers by viewModel.allUsers.collectAsState()

    var tokenInput by remember { mutableStateOf(viewModel.preferences.botToken) }
    var showToken by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // --- Hero Status Banner ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hero_status_card"),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            shape = RoundedCornerShape(18.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyberCyan, CyberBorder)))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Marco File Host Engine",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (botUsername.isNotBlank()) "@$botUsername" else "Telegram Bot Host",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (botFirstName.isNotBlank()) {
                            Text(
                                text = botFirstName,
                                color = CyberNeonBlue,
                                fontSize = 13.sp
                            )
                        }
                    }

                    StatusBadge(isOnline = isRunning)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Start/Stop Toggle Button
                Button(
                    onClick = { viewModel.toggleService() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("toggle_service_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) OfflineRed else CyberCyan,
                        contentColor = if (isRunning) Color.White else Color(0xFF00363D)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "STOP 24/7 HOST SERVICE" else "START 24/7 HOST SERVICE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Metrics Grid (4 cards) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "UPTIME",
                value = uptime,
                icon = "⏱️",
                modifier = Modifier.weight(1f),
                highlightColor = if (isRunning) OnlineGreen else TextMuted
            )
            MetricStatCard(
                title = "LATENCY",
                value = if (pingLatency != null) "${pingLatency}ms" else "-- ms",
                icon = "⚡",
                modifier = Modifier.weight(1f),
                highlightColor = CyberCyan
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "ACTIVE SCRIPTS",
                value = "$runningCount / ${files.size}",
                icon = "🤖",
                modifier = Modifier.weight(1f),
                highlightColor = WarningOrange
            )
            MetricStatCard(
                title = "ACTIVE USERS",
                value = "${totalUsers.size}",
                icon = "👥",
                modifier = Modifier.weight(1f),
                highlightColor = CyberNeonBlue
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- HTTP Keep-Alive Server Status Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyberBorder, CyberBorder)))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🌐", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Flask Keep-Alive Ping Server",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Port ${viewModel.preferences.httpPort} • Returns: \"I'am Marco File Host\"",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OnlineGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isRunning) "LISTENING" else "STANDBY",
                            color = if (isRunning) OnlineGreen else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Web Host: http://localhost:${viewModel.preferences.httpPort}/",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("http://localhost:${viewModel.preferences.httpPort}/")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.testTag("open_web_dashboard_btn")
                    ) {
                        Text("Open Web 🌐", color = CyberCyan, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Bot Token Configuration Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bot_token_card"),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyberBorder, CyberBorder)))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔑 Bot Token (Instant Activation)",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter your Telegram Bot Token to automatically start hosting and running bots 24/7.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Telegram Bot Token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Hide" else "Show",
                                tint = TextSecondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bot_token_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.updateToken(tokenInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_token_button")
                    ) {
                        Text("Save & Connect", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        enabled = !isTesting,
                        modifier = Modifier.testTag("test_token_button"),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(CyberBorder, CyberBorder)))
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberCyan, strokeWidth = 2.dp)
                        } else {
                            Text("Test Ping", color = CyberNeonBlue)
                        }
                    }
                }

                AnimatedVisibility(visible = connectionResult != null) {
                    connectionResult?.let { result ->
                        Text(
                            text = result,
                            color = if (result.startsWith("✅")) OnlineGreen else OfflineRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
