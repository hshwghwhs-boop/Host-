package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.BotHostViewModel

@Composable
fun SettingsTab(
    viewModel: BotHostViewModel,
    modifier: Modifier = Modifier
) {
    val prefs = viewModel.preferences
    val context = LocalContext.current

    var token by remember { mutableStateOf(prefs.botToken) }
    var ownerId by remember { mutableStateOf(prefs.ownerId.toString()) }
    var adminId by remember { mutableStateOf(prefs.adminId.toString()) }
    var ownerUsername by remember { mutableStateOf(prefs.ownerUsername) }
    var updateChannel by remember { mutableStateOf(prefs.updateChannel) }
    var approvalRequired by remember { mutableStateOf(prefs.adminApprovalRequired) }

    var wakeLockEnabled by remember { mutableStateOf(prefs.wakeLockEnabled) }
    var autoStartOnBoot by remember { mutableStateOf(prefs.autoStartOnBoot) }
    var httpPort by remember { mutableStateOf(prefs.httpPort.toString()) }
    var isHttpEnabled by remember { mutableStateOf(prefs.isHttpKeepAliveEnabled) }

    val scrollState = rememberScrollState()

    // Battery optimization check
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 24/7 Keep-Alive Settings Card ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("keepalive_settings_card"),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚡ 24/7 Keep-Alive & Background Execution", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Settings to ensure the bot host stays on 24/7 without being killed by the Android OS.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // WakeLock Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CPU Partial WakeLock", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Prevents phone CPU from sleeping when the screen turns off", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = wakeLockEnabled,
                        onCheckedChange = { wakeLockEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto start on boot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start on Device Boot", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Resumes bot hosting immediately after device restarts", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoStartOnBoot,
                        onCheckedChange = { autoStartOnBoot = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // HTTP Keep-Alive Server
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Flask HTTP Keep-Alive Server", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Runs HTTP server on port $httpPort for external uptime monitors", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isHttpEnabled,
                        onCheckedChange = { isHttpEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Battery Optimization Exemption
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("battery_opt_btn"),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder))
                ) {
                    Icon(
                        imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnoringBatteryOptimizations) OnlineGreen else WarningOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isIgnoringBatteryOptimizations) "Battery Unrestricted (Optimized for 24/7) ✅" else "Disable Battery Optimization (Recommended)",
                        color = if (isIgnoringBatteryOptimizations) OnlineGreen else WarningOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- Bot & Channel Configuration Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🤖 Bot & Telegram Credentials", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bot Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ownerId,
                        onValueChange = { ownerId = it },
                        label = { Text("Owner ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("settings_owner_id")
                    )
                    OutlinedTextField(
                        value = adminId,
                        onValueChange = { adminId = it },
                        label = { Text("Admin ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("settings_admin_id")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ownerUsername,
                        onValueChange = { ownerUsername = it },
                        label = { Text("Owner Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = updateChannel,
                        onValueChange = { updateChannel = it },
                        label = { Text("Update Channel") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require Admin Approval", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Free users' uploaded files require approval before running", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = approvalRequired,
                        onCheckedChange = { approvalRequired = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // --- Save Button ---
        Button(
            onClick = {
                val oId = ownerId.toLongOrNull() ?: prefs.ownerId
                val aId = adminId.toLongOrNull() ?: prefs.adminId
                val port = httpPort.toIntOrNull() ?: 8080

                viewModel.saveConfig(
                    token = token,
                    ownerId = oId,
                    adminId = aId,
                    ownerUsername = ownerUsername,
                    updateChannel = updateChannel,
                    approvalRequired = approvalRequired,
                    autoStartOnBoot = autoStartOnBoot,
                    wakeLockEnabled = wakeLockEnabled,
                    httpPort = port,
                    isHttpEnabled = isHttpEnabled
                )
                Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_settings_btn")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF00363D))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save All Settings", color = Color(0xFF00363D), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
