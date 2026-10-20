package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApprovalsUsersTab(
    viewModel: BotHostViewModel,
    modifier: Modifier = Modifier
) {
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val subscriptions by viewModel.allSubscriptions.collectAsState()
    val admins by viewModel.allAdmins.collectAsState()
    val users by viewModel.allUsers.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Approvals (${pendingFiles.size})", "Subscriptions (${subscriptions.size})", "Admins (${admins.size})", "Users (${users.size})")

    var showAddSubDialog by remember { mutableStateOf(false) }
    var showAddAdminDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = CyberCardNavy,
            contentColor = CyberCyan,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSubTab == index) CyberCyan else TextSecondary
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedSubTab) {
            0 -> {
                // Approvals Tab
                if (pendingFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("✅ No pending files requiring admin approval.", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pendingFiles, key = { "${it.userId}_${it.fileName}" }) { file ->
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("pending_file_${file.fileName}"),
                                colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(WarningOrange.copy(alpha = 0.5f))),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.fileName,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(WarningOrange.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(file.fileType.uppercase(), color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Uploaded by User ID: ${file.userId}", color = TextSecondary, fontSize = 12.sp)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.rejectFile(file) },
                                            modifier = Modifier.testTag("reject_btn_${file.fileName}")
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = OfflineRed, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reject", color = OfflineRed, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = { viewModel.approveFile(file) },
                                            colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                                            modifier = Modifier.testTag("approve_btn_${file.fileName}")
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Approve & Run", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Subscriptions Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Premium Subscriptions", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Button(
                        onClick = { showAddSubDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Sub", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (subscriptions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                        Text("No active premium subscriptions.", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subscriptions, key = { it.userId }) { sub ->
                            val daysLeft = ((sub.expiryTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sub.expiryTimestamp))
                            val isExpired = sub.expiryTimestamp <= System.currentTimeMillis()

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("User ID: ${sub.userId}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Text(
                                            text = if (isExpired) "Expired on $dateStr" else "Expires: $dateStr ($daysLeft days left)",
                                            color = if (isExpired) OfflineRed else OnlineGreen,
                                            fontSize = 12.sp
                                        )
                                    }

                                    IconButton(onClick = { viewModel.removeSubscription(sub.userId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Admins Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Configured Admins", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Button(
                        onClick = { showAddAdminDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Admin", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(admins, key = { it.userId }) { admin ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (admin.userId == viewModel.preferences.ownerId) "👑" else "🛡️",
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Admin ID: ${admin.userId}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = if (admin.userId == viewModel.preferences.ownerId) "Primary Owner" else "Co-Admin",
                                            color = if (admin.userId == viewModel.preferences.ownerId) WarningOrange else CyberCyan,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (admin.userId != viewModel.preferences.ownerId) {
                                    IconButton(onClick = { viewModel.removeAdmin(admin.userId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Demote", tint = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Users Directory
                if (users.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                        Text("No active users recorded yet.", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(users, key = { it.userId }) { user ->
                            val lastSeenStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(user.lastSeenTime))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CyberCardNavy),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberBorder)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${user.firstName ?: "User"} (@${user.username ?: "none"})",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text("ID: ${user.userId} • Last: $lastSeenStr", color = TextSecondary, fontSize = 11.sp)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CyberCardElevated)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("${user.messageCount} msgs", color = CyberCyan, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubDialog) {
        var subIdInput by remember { mutableStateOf("") }
        var daysInput by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { showAddSubDialog = false },
            title = { Text("Grant Premium Subscription", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = subIdInput,
                        onValueChange = { subIdInput = it },
                        label = { Text("User Telegram ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it },
                        label = { Text("Duration (Days)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = subIdInput.toLongOrNull()
                        val d = daysInput.toIntOrNull() ?: 30
                        if (uid != null) {
                            viewModel.addSubscription(uid, d)
                            showAddSubDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberCardNavy
        )
    }

    if (showAddAdminDialog) {
        var adminIdInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAdminDialog = false },
            title = { Text("Promote User to Admin", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = adminIdInput,
                    onValueChange = { adminIdInput = it },
                    label = { Text("User Telegram ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = adminIdInput.toLongOrNull()
                        if (uid != null) {
                            viewModel.addAdmin(uid)
                            showAddAdminDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Promote", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAdminDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberCardNavy
        )
    }
}
