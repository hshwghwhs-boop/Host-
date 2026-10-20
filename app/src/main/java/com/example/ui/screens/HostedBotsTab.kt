package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HostedFileEntity
import com.example.ui.components.AddScriptDialog
import com.example.ui.components.HostedScriptCard
import com.example.ui.components.ViewLogsDialog
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.CyberCardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.BotHostViewModel
import kotlinx.coroutines.launch

@Composable
fun HostedBotsTab(
    viewModel: BotHostViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.allFiles.collectAsState()
    val activeFiles = files.filter { it.status != "PENDING" }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var viewingLogsFile by remember { mutableStateOf<HostedFileEntity?>(null) }
    var currentLogsText by remember { mutableStateOf("") }

    if (showAddDialog) {
        AddScriptDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { userId, fileName, fileType, content ->
                viewModel.createScriptLocally(userId, fileName, fileType, content)
            }
        )
    }

    viewingLogsFile?.let { targetFile ->
        ViewLogsDialog(
            fileName = targetFile.fileName,
            logs = currentLogsText,
            onDismiss = { viewingLogsFile = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hosted Bots & Scripts",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${activeFiles.size} scripts managed on device",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.runAllScripts() },
                    modifier = Modifier.testTag("run_all_scripts_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run All", color = OnlineGreen, fontSize = 12.sp)
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.testTag("deploy_script_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Deploy", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeFiles.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📂", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Hosted Scripts Yet",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Send a .py, .js, or .zip file directly to the Telegram bot or deploy a script from the app.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.createScriptLocally(
                                userId = viewModel.preferences.ownerId,
                                fileName = "echo_bot.py",
                                fileType = "py",
                                content = "# Sample Telegram Echo Bot\nimport time\nprint('[EchoBot] Started 24/7 background worker')\n"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardElevated)
                    ) {
                        Text("Deploy Starter Echo Bot", color = CyberCyan)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(activeFiles, key = { "${it.userId}_${it.fileName}" }) { file ->
                    HostedScriptCard(
                        file = file,
                        isRunning = file.status == "RUNNING",
                        onStart = { viewModel.startScript(file) },
                        onStop = { viewModel.stopScript(file.userId, file.fileName) },
                        onRestart = { viewModel.restartScript(file) },
                        onDelete = { viewModel.deleteScript(file.userId, file.fileName) },
                        onViewLogs = {
                            scope.launch {
                                currentLogsText = viewModel.getScriptLogs(file.userId, file.fileName)
                                viewingLogsFile = file
                            }
                        }
                    )
                }
            }
        }
    }
}
