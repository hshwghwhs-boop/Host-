package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HostedFileEntity
import com.example.data.model.LogEntryEntity
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.CyberCardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalText
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun StatusBadge(isOnline: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isOnline) OnlineGreen.copy(alpha = 0.15f) else OfflineRed.copy(alpha = 0.15f)
    val dotColor = if (isOnline) OnlineGreen else OfflineRed
    val textColor = if (isOnline) OnlineGreen else OfflineRed
    val text = if (isOnline) "24/7 ONLINE" else "OFFLINE"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, dotColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = CyberCyan
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(text = icon, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = highlightColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HostedScriptCard(
    file: HostedFileEntity,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    onViewLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        file.status == "PENDING" -> WarningOrange
        isRunning -> OnlineGreen
        else -> OfflineRed
    }
    val statusLabel = when {
        file.status == "PENDING" -> "⏳ PENDING"
        isRunning -> "🟢 RUNNING"
        else -> "🔴 STOPPED"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("script_card_${file.fileName}"),
        colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (file.fileType == "py") Color(0xFF3776AB).copy(alpha = 0.25f)
                                else Color(0xFFF7DF1E).copy(alpha = 0.25f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = file.fileType.uppercase(),
                            color = if (file.fileType == "py") Color(0xFF38BDF8) else Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.fileName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "User: ${file.userId}",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                if (file.pid > 0 && isRunning) {
                    Text(
                        text = "PID: ${file.pid}",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onViewLogs,
                    modifier = Modifier.testTag("logs_btn_${file.fileName}")
                ) {
                    Text("Logs 📜", color = CyberCyan, fontSize = 12.sp)
                }

                if (isRunning) {
                    IconButton(
                        onClick = onRestart,
                        modifier = Modifier.testTag("restart_btn_${file.fileName}")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = WarningOrange)
                    }
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.testTag("stop_btn_${file.fileName}")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = OfflineRed)
                    }
                } else {
                    IconButton(
                        onClick = onStart,
                        modifier = Modifier.testTag("start_btn_${file.fileName}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = OnlineGreen)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_btn_${file.fileName}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
                }
            }
        }
    }
}

@Composable
fun AddScriptDialog(
    onDismiss: () -> Unit,
    onConfirm: (userId: Long, fileName: String, fileType: String, content: String) -> Unit
) {
    var userIdText by remember { mutableStateOf("8714511542") }
    var fileName by remember { mutableStateOf("bot.py") }
    var fileType by remember { mutableStateOf("py") }
    var scriptContent by remember {
        mutableStateOf(
            "# -*- coding: utf-8 -*-\nimport time\nprint('Bot initialized successfully!')\nwhile True:\n    time.sleep(10)\n"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add / Deploy New Bot Script", color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = userIdText,
                    onValueChange = { userIdText = it },
                    label = { Text("Owner / User ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_script_user_id")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = {
                            fileName = it
                            if (it.endsWith(".js")) fileType = "js"
                            else if (it.endsWith(".py")) fileType = "py"
                        },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_script_name")
                    )
                    OutlinedTextField(
                        value = fileType,
                        onValueChange = { fileType = it },
                        label = { Text("Type") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = scriptContent,
                    onValueChange = { scriptContent = it },
                    label = { Text("Script Code") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("add_script_content"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uid = userIdText.toLongOrNull() ?: 8714511542L
                    onConfirm(uid, fileName, fileType, scriptContent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.testTag("add_script_confirm_btn")
            ) {
                Text("Deploy", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CyberCardNavy
    )
}

@Composable
fun ViewLogsDialog(
    fileName: String,
    logs: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📜 Logs: $fileName", color = TextPrimary, fontSize = 16.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalBackground)
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                LazyColumn {
                    item {
                        Text(
                            text = if (logs.isBlank()) "(Empty log file)" else logs,
                            color = TerminalText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCardElevated)
            ) {
                Text("Close", color = TextPrimary)
            }
        },
        containerColor = CyberCardNavy
    )
}
