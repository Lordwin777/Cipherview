package com.example.cipherview.ui.screens.receive

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cipherview.data.model.ShareDirection
import com.example.cipherview.data.model.SharedDocumentRecord
import com.example.cipherview.data.network.LocalTransferEngine
import com.example.cipherview.data.network.NsdDiscoveryManager
import com.example.cipherview.data.network.TransferStatus
import com.example.cipherview.data.repository.LocalVaultRepository
import com.example.cipherview.data.security.AccessCodeGenerator
import com.example.cipherview.data.security.CryptoEngine
import com.example.cipherview.data.security.QrCodeUtil
import com.example.cipherview.theme.*
import com.example.cipherview.ui.components.QrScannerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveDocumentScreen(
    repository: LocalVaultRepository,
    discoveryManager: NsdDiscoveryManager,
    transferEngine: LocalTransferEngine,
    onBack: () -> Unit,
    onNavigateToDocument: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by repository.userProfile.collectAsState()
    val transferProgress by transferEngine.transferProgress.collectAsState()
    val allDocuments by repository.documents.collectAsState()

    var boundPort by remember { mutableStateOf(8998) }
    var localIp by remember { mutableStateOf<String?>("127.0.0.1") }
    var showQrCode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    var newlyReceivedDocumentId by remember { mutableStateOf<String?>(null) }
    var newlyReceivedDocName by remember { mutableStateOf<String?>(null) }
    var selectedDocumentId by remember { mutableStateOf<String?>(null) }

    var typedAccessCode by remember { mutableStateOf("") }
    var codeErrorMessage by remember { mutableStateOf<String?>(null) }

    val myNickname = profile?.currentNickname ?: "User"

    // Active document to decrypt: newly received > manually selected > latest received > latest vault doc
    val activeDoc: SharedDocumentRecord? = remember(allDocuments, newlyReceivedDocumentId, selectedDocumentId) {
        if (newlyReceivedDocumentId != null) {
            allDocuments.firstOrNull { it.id == newlyReceivedDocumentId }
        } else if (selectedDocumentId != null) {
            allDocuments.firstOrNull { it.id == selectedDocumentId }
        } else {
            allDocuments.firstOrNull { it.direction == ShareDirection.RECEIVED }
                ?: allDocuments.firstOrNull()
        }
    }

    // File Picker for importing .cview file manually
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        if (CryptoEngine.isPdf(bytes)) {
                            codeErrorMessage = "Selected file is an unencrypted PDF. Use 'Share Document' to encrypt it with an Access Code."
                            Toast.makeText(context, "Unencrypted PDF detected. Use Share to encrypt.", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (!CryptoEngine.isEncryptedPackage(bytes)) {
                            codeErrorMessage = "Selected file is not a valid CipherView (.cview) encrypted package."
                            Toast.makeText(context, "Invalid format: File is not a CipherView (.cview) bundle.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        val record = repository.saveReceivedDocument(bytes)
                        newlyReceivedDocumentId = record.id
                        selectedDocumentId = record.id
                        newlyReceivedDocName = record.fileName
                        codeErrorMessage = null
                        Toast.makeText(context, "Encrypted package '${record.fileName}' imported!", Toast.LENGTH_SHORT).show()

                        // If user already typed or scanned a code, open immediately!
                        if (typedAccessCode.isNotBlank()) {
                            onNavigateToDocument(record.id, typedAccessCode)
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: "Failed to import .cview package"
                    codeErrorMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Start receiving server and register NSD on enter
    DisposableEffect(myNickname) {
        val port = transferEngine.startReceivingServer()
        boundPort = port
        localIp = transferEngine.getLocalIpAddress() ?: "127.0.0.1"
        discoveryManager.registerService(myNickname, port)

        transferEngine.onPackageReceived = { packageBytes ->
            scope.launch {
                try {
                    val record = repository.saveReceivedDocument(packageBytes)
                    newlyReceivedDocumentId = record.id
                    selectedDocumentId = record.id
                    newlyReceivedDocName = record.fileName
                    codeErrorMessage = null

                    // If user had already pre-entered the code, open right away
                    if (typedAccessCode.isNotBlank()) {
                        onNavigateToDocument(record.id, typedAccessCode)
                    }
                } catch (e: Exception) {
                    codeErrorMessage = "Failed to save received package: ${e.localizedMessage}"
                }
            }
        }

        onDispose {
            discoveryManager.unregisterService()
            transferEngine.stopServer()
            transferEngine.onPackageReceived = null
            transferEngine.resetProgress()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Receive & Unlock", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Transfer in progress indicator
            if (transferProgress.status == TransferStatus.TRANSFERRING) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyanDark, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = VaultSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "RECEIVING ENCRYPTED PACKAGE",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = { transferProgress.progressFraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = CyanNeon,
                                trackColor = VaultSurfaceVariant
                            )
                            Text(
                                text = "${transferProgress.bytesTransferred} / ${transferProgress.totalBytes} bytes",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // =================================================================
            // HERO SECTION: SCAN QR CODE OR TYPE ACCESS CODE TO UNLOCK
            // =================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, if (activeDoc != null) CyanNeon else CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "UNLOCK PROTECTED DOCUMENT",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Target document chip / selector
                        if (activeDoc != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, CyanDark, RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = CyanNeon,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = activeDoc.fileName,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Shared by: ${activeDoc.sharedWithNickname} • ${activeDoc.viewsCount}/${activeDoc.maxViews} Views",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (allDocuments.size > 1) {
                                            TextButton(
                                                onClick = {
                                                    // Cycle to next document
                                                    val nextIndex = (allDocuments.indexOfFirst { it.id == activeDoc.id } + 1) % allDocuments.size
                                                    selectedDocumentId = allDocuments[nextIndex].id
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Text("Change", color = CyanNeon, fontSize = 11.sp)
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                repository.deleteDocument(activeDoc.id)
                                                selectedDocumentId = null
                                                newlyReceivedDocumentId = null
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete document",
                                                tint = TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // No document yet
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Waiting for document...",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Receive via Wi-Fi or import .cview file",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { importLauncher.launch("*/*") },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        border = BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Select File", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // ACTION 1: SCAN QR CODE TO OPEN DIRECTLY
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24))
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan QR Code to Open", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // DIVIDER: OR TYPE CODE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = CardBorder)
                            Text(
                                text = "  OR TYPE ACCESS CODE  ",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = CardBorder)
                        }

                        // ACTION 2: ENTER ACCESS CODE INPUT
                        OutlinedTextField(
                            value = typedAccessCode,
                            onValueChange = {
                                typedAccessCode = it.uppercase()
                                if (codeErrorMessage != null) codeErrorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. X7K9P2", color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = CyanNeon,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (typedAccessCode.isNotBlank()) {
                                        if (activeDoc != null) {
                                            onNavigateToDocument(activeDoc.id, typedAccessCode)
                                        } else {
                                            codeErrorMessage = "Please select or import a document first"
                                            importLauncher.launch("*/*")
                                        }
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = VaultSurfaceVariant,
                                unfocusedContainerColor = VaultSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (codeErrorMessage != null) {
                            Text(
                                text = codeErrorMessage!!,
                                color = SecurityRed,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        // ACTION 3: DECRYPT & OPEN BUTTON
                        Button(
                            onClick = {
                                if (activeDoc != null) {
                                    if (typedAccessCode.isNotBlank()) {
                                        onNavigateToDocument(activeDoc.id, typedAccessCode)
                                    } else {
                                        // Open viewer and let user enter or scan there
                                        onNavigateToDocument(activeDoc.id, null)
                                    }
                                } else {
                                    codeErrorMessage = "Please select or import a .cview file first"
                                    importLauncher.launch("*/*")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (typedAccessCode.isNotBlank() && activeDoc != null) EmeraldSecurity else VaultSurfaceVariant,
                                contentColor = if (typedAccessCode.isNotBlank() && activeDoc != null) Color(0xFF00210B) else CyanNeon
                            ),
                            border = if (typedAccessCode.isNotBlank() && activeDoc != null) null else BorderStroke(1.dp, CyanDark)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (typedAccessCode.isNotBlank()) "Decrypt & Open Document" else "Open in Protected Viewer",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // =================================================================
            // LISTENER & DIRECT NETWORK ENDPOINT
            // =================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(VaultSurfaceVariant)
                                    .border(1.5.dp, EmeraldSecurity, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = EmeraldSecurity,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Listening for Nearby Senders",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Broadcasting as '$myNickname' on Wi-Fi & Bluetooth",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        HorizontalDivider(color = CardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DIRECT NETWORK ENDPOINT", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("IP: $localIp : $boundPort", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { showQrCode = !showQrCode },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                                border = BorderStroke(1.dp, CyanDark),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (showQrCode) "Hide" else "QR", fontSize = 11.sp)
                            }
                        }

                        AnimatedVisibility(visible = showQrCode) {
                            val qrUri = "cview://$localIp:$boundPort/$myNickname"
                            val qrBitmap = remember(qrUri) { QrCodeUtil.generateQrBitmap(qrUri, 350) }
                            if (qrBitmap != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                            .padding(10.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap,
                                            contentDescription = "Connection QR",
                                            modifier = Modifier.size(160.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Sender can scan to connect directly", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // IMPORT OFFLINE PACKAGE
            // =================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "IMPORT OFFLINE PACKAGE",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "If you received a '.cview' file via flash drive, local file transfer, or Nearby Share, import it directly into CipherView.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { importLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultSurfaceVariant, contentColor = TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select .cview Package File", fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // QR Code Scanner Camera Dialog
    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onScanned = { scanned ->
                showQrScanner = false
                val clean = AccessCodeGenerator.normalize(scanned)
                typedAccessCode = clean

                if (activeDoc != null) {
                    Toast.makeText(context, "Scanned Code: $clean! Decrypting document...", Toast.LENGTH_SHORT).show()
                    onNavigateToDocument(activeDoc.id, clean)
                } else {
                    Toast.makeText(context, "Scanned Code: $clean! Please select .cview package to unlock.", Toast.LENGTH_LONG).show()
                    importLauncher.launch("*/*")
                }
            }
        )
    }
}
