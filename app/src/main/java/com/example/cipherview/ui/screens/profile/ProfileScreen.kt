package com.example.cipherview.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cipherview.data.repository.LocalVaultRepository
import com.example.cipherview.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: LocalVaultRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by repository.userProfile.collectAsState()

    var newNicknameInput by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    val currentNickname = profile?.currentNickname ?: "User"
    val nicknameHistory = profile?.nicknameHistory?.sortedByDescending { it.timestamp } ?: emptyList()
    val deviceId = profile?.deviceId ?: "cview-local"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Vault Identity & Nickname", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Current Identity Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Large Avatar
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CyanDark)
                                .border(2.dp, CyanNeon, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentNickname.take(1).uppercase(),
                                color = CyanNeon,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = currentNickname,
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Device ID: $deviceId",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        if (!isEditing) {
                            OutlinedButton(
                                onClick = {
                                    newNicknameInput = currentNickname
                                    isEditing = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanDark)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Nickname", fontSize = 13.sp)
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newNicknameInput,
                                    onValueChange = { newNicknameInput = it },
                                    label = { Text("New Nickname", color = TextMuted) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyanNeon,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedContainerColor = VaultSurfaceVariant,
                                        unfocusedContainerColor = VaultSurfaceVariant
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (newNicknameInput.trim().length in 2..24) {
                                                repository.updateNickname(newNicknameInput.trim())
                                                isEditing = false
                                                Toast.makeText(context, "Nickname updated to '${newNicknameInput.trim()}'", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (newNicknameInput.trim().length in 2..24) {
                                                repository.updateNickname(newNicknameInput.trim())
                                                isEditing = false
                                                Toast.makeText(context, "Nickname updated to '${newNicknameInput.trim()}'", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Nickname must be 2 to 24 characters", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Save", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { isEditing = false },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Nickname History Section
            item {
                Text(
                    text = "LOCAL NICKNAME HISTORY",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Previous document activities retain the nickname used at the time instead of replacing historical records.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            items(nicknameHistory) { item ->
                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (item.isCurrent) CyanDark else CardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isCurrent) CyanNeon else TextMuted)
                            )
                            Column {
                                Text(
                                    text = item.nickname,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (item.isCurrent) "Current Identity" else "Adopted on $dateStr",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (item.isCurrent) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyanDark)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Current",
                                    color = CyanNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "Previous",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Privacy Architecture Explainer Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldSecurity, modifier = Modifier.size(18.dp))
                            Text(
                                text = "PRIVACY ARCHITECTURE",
                                color = EmeraldSecurity,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text("• No cloud servers: documents are never uploaded.", color = TextSecondary, fontSize = 12.sp)
                        Text("• No email or phone numbers required.", color = TextSecondary, fontSize = 12.sp)
                        Text("• No usernames or passwords.", color = TextSecondary, fontSize = 12.sp)
                        Text("• Hardware Keystore: cryptographic material is secured locally.", color = TextSecondary, fontSize = 12.sp)
                        Text("• Offline local sharing via Wi-Fi and Bluetooth.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
