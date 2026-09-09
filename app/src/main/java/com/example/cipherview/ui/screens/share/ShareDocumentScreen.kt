package com.example.cipherview.ui.screens.share

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cipherview.data.network.DiscoveredPeer
import com.example.cipherview.data.network.LocalTransferEngine
import com.example.cipherview.data.network.NsdDiscoveryManager
import com.example.cipherview.data.network.TransferStatus
import com.example.cipherview.data.network.TransportType
import com.example.cipherview.data.repository.LocalVaultRepository
import com.example.cipherview.data.security.AccessCodeGenerator
import com.example.cipherview.data.security.CryptoEngine
import com.example.cipherview.theme.*
import com.example.cipherview.ui.components.AccessCodeDisplay
import com.example.cipherview.ui.components.QrScannerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDocumentScreen(
    repository: LocalVaultRepository,
    discoveryManager: NsdDiscoveryManager,
    transferEngine: LocalTransferEngine,
    onBack: () -> Unit,
    onNavigateToDocument: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by repository.userProfile.collectAsState()
    val peers by discoveryManager.peers.collectAsState()
    val transferProgress by transferEngine.transferProgress.collectAsState()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Restrictions
    var selectedExpiryHours by remember { mutableStateOf<Int?>(24) } // Default 24 hours
    var selectedMaxViews by remember { mutableStateOf<Int?>(3) } // Default 3 views

    // Generated state
    var isEncrypted by remember { mutableStateOf(false) }
    var generatedAccessCode by remember { mutableStateOf<String?>(null) }
    var encryptedPackageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var savedRecordId by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    val myNickname = profile?.currentNickname ?: "User"

    // PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        var name = "Protected_Document.pdf"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                name = cursor.getString(nameIndex)
                            }
                        }
                        selectedFileName = name
                        selectedFileBytes = bytes
                        // Reset encrypted package if new file picked
                        isEncrypted = false
                        generatedAccessCode = null
                        encryptedPackageBytes = null
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Export .cview container launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && encryptedPackageBytes != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(encryptedPackageBytes!!)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Encrypted package exported (.cview)", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Start discovery when entering screen
    DisposableEffect(Unit) {
        discoveryManager.startDiscovery(myNickname)
        onDispose {
            discoveryManager.stopDiscovery()
            transferEngine.resetProgress()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Share Sensitive PDF", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: Select Document
            item {
                Text(
                    text = "1. SELECT PDF DOCUMENT",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (selectedFileBytes != null) CyanDark else CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selectedFileName == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse PDF", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        // Generate a secure demo PDF in memory
                                        val demoDoc = PdfDocument()
                                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                                        val page = demoDoc.startPage(pageInfo)
                                        val canvas = page.canvas
                                        val paint = Paint().apply {
                                            color = AndroidColor.BLACK
                                            textSize = 20f
                                            isFakeBoldText = true
                                        }
                                        canvas.drawText("CONFIDENTIAL PROJECT REPORT", 50f, 80f, paint)
                                        paint.textSize = 14f
                                        paint.isFakeBoldText = false
                                        canvas.drawText("Protected by CipherView Local-First Digital Vault", 50f, 120f, paint)
                                        canvas.drawText("Sender Nickname: $myNickname", 50f, 150f, paint)
                                        canvas.drawText("Encrypted with AES-256-GCM before transmission.", 50f, 180f, paint)
                                        canvas.drawText("This document is restricted by sender policies.", 50f, 210f, paint)
                                        demoDoc.finishPage(page)

                                        val baos = ByteArrayOutputStream()
                                        demoDoc.writeTo(baos)
                                        demoDoc.close()

                                        selectedFileName = "Project_Report.pdf"
                                        selectedFileBytes = baos.toByteArray()
                                        isEncrypted = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sample PDF", fontSize = 13.sp)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CyanDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(selectedFileName ?: "", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("${(selectedFileBytes?.size ?: 0) / 1024} KB • Ready to encrypt", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                TextButton(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                                    Text("Change", color = CyanNeon, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: Configure Restrictions
            item {
                Text(
                    text = "2. SENDER-CONTROLLED RESTRICTIONS",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Expiration selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = SecurityAmber, modifier = Modifier.size(16.dp))
                                Text("Access Expiration", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1 to "1 Hour", 24 to "24 Hours", 168 to "7 Days", null to "Never").forEach { (hours, label) ->
                                    FilterChip(
                                        selected = selectedExpiryHours == hours,
                                        onClick = { selectedExpiryHours = hours },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SecurityAmberDark,
                                            selectedLabelColor = SecurityAmber,
                                            containerColor = VaultSurfaceVariant,
                                            labelColor = TextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = if (selectedExpiryHours == hours) SecurityAmber else CardBorder,
                                            enabled = true,
                                            selected = selectedExpiryHours == hours
                                        )
                                    )
                                }
                            }
                        }

                        // View Limit selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                                Text("Maximum Views", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1 to "1 View (Burn)", 3 to "3 Views", 5 to "5 Views", null to "Unlimited").forEach { (views, label) ->
                                    FilterChip(
                                        selected = selectedMaxViews == views,
                                        onClick = { selectedMaxViews = views },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanDark,
                                            selectedLabelColor = CyanNeon,
                                            containerColor = VaultSurfaceVariant,
                                            labelColor = TextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = if (selectedMaxViews == views) CyanNeon else CardBorder,
                                            enabled = true,
                                            selected = selectedMaxViews == views
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: Encrypt Action & Access Code
            item {
                if (!isEncrypted) {
                    Button(
                        onClick = {
                            val pdfBytes = selectedFileBytes
                            val fileName = selectedFileName
                            if (pdfBytes != null && fileName != null) {
                                val code = AccessCodeGenerator.generate()
                                val expiresAt = selectedExpiryHours?.let { System.currentTimeMillis() + it * 3600 * 1000L }

                                val encrypted = CryptoEngine.encryptDocument(
                                    pdfBytes = pdfBytes,
                                    accessCode = code,
                                    fileName = fileName,
                                    senderNickname = myNickname,
                                    expiresAt = expiresAt,
                                    maxViews = selectedMaxViews
                                )

                                scope.launch {
                                    val record = repository.saveSentDocument(
                                        fileName = fileName,
                                        fileSizeBytes = encrypted.size.toLong(),
                                        sharedWithNickname = "Nearby Recipient",
                                        accessCode = code,
                                        encryptedPackageBytes = encrypted,
                                        expiresAt = expiresAt,
                                        maxViews = selectedMaxViews
                                    )
                                    savedRecordId = record.id
                                    generatedAccessCode = code
                                    encryptedPackageBytes = encrypted
                                    isEncrypted = true
                                    Toast.makeText(context, "Encrypted locally with AES-256-GCM!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Please select or create a PDF first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24)),
                        enabled = selectedFileBytes != null
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Encrypt Document with AES-256-GCM", fontWeight = FontWeight.Bold)
                    }
                } else {
                    generatedAccessCode?.let { code ->
                        AccessCodeDisplay(accessCode = code)
                    }
                }
            }

            // STEP 4: Direct Local Sharing (Wi-Fi NSD, Bluetooth, Export)
            if (isEncrypted && encryptedPackageBytes != null) {
                item {
                    Text(
                        text = "3. DIRECT LOCAL SHARING (NO SERVERS)",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = VaultSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                                    Text("Nearby CipherView Devices", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanNeon)
                            }

                            Button(
                                onClick = { showQrScanner = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24))
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Recipient's Screen QR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            if (peers.isEmpty()) {
                                Text(
                                    text = "Scanning local Wi-Fi / Hotspot for nearby devices... Ensure recipient has opened CipherView's 'Receive' tab.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    peers.forEach { peer ->
                                        PeerListItem(
                                            peer = peer,
                                            onSendClick = {
                                                scope.launch {
                                                    val success = if (peer.transportType == TransportType.WIFI_NSD && peer.ipAddress != null && peer.port != null) {
                                                        transferEngine.sendPackageViaWifi(
                                                            ipAddress = peer.ipAddress,
                                                            port = peer.port,
                                                            packageBytes = encryptedPackageBytes!!,
                                                            peerNickname = peer.nickname,
                                                            fileName = selectedFileName ?: "Protected.pdf"
                                                        )
                                                    } else if (peer.bluetoothAddress != null) {
                                                        transferEngine.sendPackageViaBluetooth(
                                                            deviceAddress = peer.bluetoothAddress,
                                                            packageBytes = encryptedPackageBytes!!,
                                                            peerNickname = peer.nickname,
                                                            fileName = selectedFileName ?: "Protected.pdf"
                                                        )
                                                    } else false

                                                    if (success) {
                                                        Toast.makeText(context, "Document transmitted to ${peer.nickname}!", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Transfer Progress indicator
                            if (transferProgress.status == TransferStatus.TRANSFERRING) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { transferProgress.progressFraction },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = CyanNeon,
                                        trackColor = VaultSurfaceVariant
                                    )
                                    Text(
                                        text = "Sending encrypted package to ${transferProgress.peerNickname}... ${(transferProgress.progressFraction * 100).toInt()}%",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            } else if (transferProgress.status == TransferStatus.SUCCESS) {
                                Text(
                                    text = "Transfer complete! Remember to give the Access Code to the recipient.",
                                    color = EmeraldSecurity,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            HorizontalDivider(color = CardBorder)

                            // Fallback options
                            Text(
                                text = "OFFLINE FALLBACKS",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        exportLauncher.launch("${selectedFileName?.removeSuffix(".pdf") ?: "document"}.cview")
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export .cview", fontSize = 12.sp)
                                }

                                if (savedRecordId != null) {
                                    Button(
                                        onClick = { onNavigateToDocument(savedRecordId!!) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = VaultSurfaceVariant, contentColor = CyanNeon),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Open in Vault", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onScanned = { scanned ->
                showQrScanner = false
                if (scanned.startsWith("cview://")) {
                    val clean = scanned.removePrefix("cview://")
                    val parts = clean.split("/")
                    val hostPort = parts[0].split(":")
                    val ip = hostPort[0]
                    val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8998
                    val nick = parts.getOrNull(1) ?: "Recipient"
                    scope.launch {
                        Toast.makeText(context, "Sending directly to $nick at $ip:$port...", Toast.LENGTH_SHORT).show()
                        val success = transferEngine.sendPackageViaWifi(
                            ipAddress = ip,
                            port = port,
                            packageBytes = encryptedPackageBytes!!,
                            peerNickname = nick,
                            fileName = selectedFileName ?: "Protected.pdf"
                        )
                        if (success) {
                            Toast.makeText(context, "Document transmitted directly to $nick!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Direct transfer failed. Verify Wi-Fi connection.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Scanned: $scanned. Not a CipherView recipient endpoint.", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun PeerListItem(
    peer: DiscoveredPeer,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VaultSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CyanDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.nickname.take(1).uppercase(),
                    color = CyanNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(peer.nickname, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(peer.transportType.displayName, color = TextMuted, fontSize = 10.sp)
            }
        }

        Button(
            onClick = onSendClick,
            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
