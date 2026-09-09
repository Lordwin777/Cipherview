package com.example.cipherview.ui.screens.viewer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cipherview.data.model.DocumentStatus
import com.example.cipherview.data.model.ShareDirection
import com.example.cipherview.data.model.SharedDocumentRecord
import com.example.cipherview.data.repository.LocalVaultRepository
import com.example.cipherview.data.security.AccessCodeGenerator
import com.example.cipherview.data.security.CryptoEngine
import com.example.cipherview.theme.*
import com.example.cipherview.ui.components.QrScannerDialog
import com.example.cipherview.ui.components.SecurityBadge
import com.example.cipherview.ui.components.WatermarkOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedViewerScreen(
    documentId: String,
    repository: LocalVaultRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    prefilledCode: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by repository.userProfile.collectAsState()

    var record by remember { mutableStateOf<SharedDocumentRecord?>(null) }
    var enteredCode by remember { mutableStateOf(prefilledCode ?: "") }
    var isCodePromptVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    // PDF Rendering state
    var isDecrypted by remember { mutableStateOf(false) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tempDecryptedFile by remember { mutableStateOf<File?>(null) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val myNickname = profile?.currentNickname ?: "User"
    val deviceId = profile?.deviceId ?: "cview-dev"

    // Activate FLAG_SECURE on Window to resist screen capture and task-switcher previews
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            // Securely wipe ephemeral decrypted PDF file from memory cache
            try {
                pdfRenderer?.close()
                tempDecryptedFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Forward declaration of attemptDecryption handled below
    // Load document metadata from repository
    LaunchedEffect(documentId, prefilledCode) {
        val doc = repository.getDocumentById(documentId)
        record = doc
        if (doc != null) {
            if (!prefilledCode.isNullOrBlank()) {
                enteredCode = prefilledCode
            } else if (doc.direction == ShareDirection.SENT && doc.accessCode.isNotBlank()) {
                // If sender, access code is known from history
                enteredCode = doc.accessCode
            } else {
                isCodePromptVisible = true
            }
        }
    }

    // Function to render a specific page index
    fun renderPage(index: Int, renderer: PdfRenderer) {
        if (index in 0 until renderer.pageCount) {
            val page = renderer.openPage(index)
            // Render at high resolution (1.5x of page dimension)
            val width = (page.width * 1.5).toInt()
            val height = (page.height * 1.5).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            currentPageBitmap = bitmap
            currentPageIndex = index
            scale = 1f
            offset = Offset.Zero
        }
    }

    // Function to perform decryption and restriction checking
    fun attemptDecryption(codeToUse: String) {
        val currentRecord = record ?: return
        val effectiveStatus = currentRecord.computeEffectiveStatus()

        if (effectiveStatus != DocumentStatus.ACTIVE) {
            errorMessage = when (effectiveStatus) {
                DocumentStatus.EXPIRED -> "Access to this document has expired."
                DocumentStatus.VIEW_LIMIT_REACHED -> "Document view limit has been reached (${currentRecord.viewsCount}/${currentRecord.maxViews})."
                DocumentStatus.REVOKED -> "Access to this document was revoked by the sender."
                DocumentStatus.ACTIVE -> ""
            }
            return
        }

        val packageBytes = repository.getEncryptedPackageBytes(currentRecord)
        if (packageBytes == null) {
            errorMessage = "Encrypted package file not found."
            return
        }

        scope.launch(Dispatchers.IO) {
            val result = CryptoEngine.decryptDocument(packageBytes, codeToUse)
            withContext(Dispatchers.Main) {
                when (result) {
                    is CryptoEngine.DecryptResult.Success -> {
                        // Check if sender restriction expired inside package
                        if (result.expiresAt != null && System.currentTimeMillis() > result.expiresAt) {
                            errorMessage = "Document has expired according to sender security policy."
                            return@withContext
                        }

                        try {
                            // Write to ephemeral private cache file
                            val tempFile = File(context.cacheDir, "ephemeral_${UUID.randomUUID()}.pdf")
                            FileOutputStream(tempFile).use { it.write(result.pdfBytes) }
                            tempDecryptedFile = tempFile

                            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(pfd)
                            pdfRenderer = renderer
                            pageCount = renderer.pageCount

                            // Record local access event and increment view count
                            val updatedRecord = repository.recordDocumentView(documentId)
                            if (updatedRecord != null) {
                                record = updatedRecord
                            }

                            isDecrypted = true
                            isCodePromptVisible = false
                            errorMessage = null
                            renderPage(0, renderer)
                        } catch (e: Exception) {
                            errorMessage = "Error initializing PDF renderer: ${e.localizedMessage}"
                        }
                    }
                    is CryptoEngine.DecryptResult.RegularPdfNotEncrypted -> {
                        errorMessage = "This file is a standard unencrypted PDF, not an encrypted .cview package. Use 'Share Document' to encrypt it."
                    }
                    is CryptoEngine.DecryptResult.InvalidAccessCodeOrCorrupted -> {
                        errorMessage = "Incorrect Access Code. Decryption failed (AES-256-GCM authentication mismatch)."
                    }
                    is CryptoEngine.DecryptResult.Expired -> {
                        errorMessage = "Document expired on ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(result.expiredAt))}."
                    }
                    is CryptoEngine.DecryptResult.InvalidPackageFormat -> {
                        errorMessage = result.reason
                    }
                }
            }
        }
    }

    LaunchedEffect(record, prefilledCode) {
        val current = record ?: return@LaunchedEffect
        if (!prefilledCode.isNullOrBlank()) {
            attemptDecryption(prefilledCode)
        } else if (current.direction == ShareDirection.SENT && current.accessCode.isNotBlank()) {
            attemptDecryption(current.accessCode)
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 4f)
        offset += panChange
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = record?.fileName ?: "Protected Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldSecurity, modifier = Modifier.size(12.dp))
                            Text(
                                text = "Capture-Resistant Protection Active",
                                color = EmeraldSecurity,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                },
                actions = {
                    record?.let {
                        IconButton(
                            onClick = {
                                repository.deleteDocument(documentId)
                                onBack()
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Document", tint = TextMuted)
                        }
                        SecurityBadge(status = it.computeEffectiveStatus())
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultSurface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (isDecrypted && currentPageBitmap != null) {
                // Rendered PDF Page with Zoom & Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = currentPageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )

                    // Dynamic Security Watermark overlay
                    WatermarkOverlay(
                        viewerNickname = myNickname,
                        deviceId = deviceId
                    )
                }

                // Page Navigation Bar Overlay
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPageIndex > 0 && pdfRenderer != null) {
                                    renderPage(currentPageIndex - 1, pdfRenderer!!)
                                }
                            },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = if (currentPageIndex > 0) TextPrimary else TextMuted)
                        }

                        Text(
                            text = "${currentPageIndex + 1} / $pageCount",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        IconButton(
                            onClick = {
                                if (currentPageIndex < pageCount - 1 && pdfRenderer != null) {
                                    renderPage(currentPageIndex + 1, pdfRenderer!!)
                                }
                            },
                            enabled = currentPageIndex < pageCount - 1
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = if (currentPageIndex < pageCount - 1) TextPrimary else TextMuted)
                        }

                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        ) {
                            Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom", tint = CyanNeon)
                        }
                    }
                }
            } else {
                // Locked / Verification State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(VaultSurfaceVariant)
                            .border(2.dp, if (errorMessage != null) SecurityRed else CyanNeon, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (errorMessage != null) Icons.Default.LockClock else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (errorMessage != null) SecurityRed else CyanNeon,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = record?.fileName ?: "Encrypted Document",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val status = record?.computeEffectiveStatus() ?: DocumentStatus.ACTIVE
                    if (status != DocumentStatus.ACTIVE) {
                        Text(
                            text = "Access Blocked: ${status.label}",
                            color = SecurityRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (status) {
                                DocumentStatus.EXPIRED -> "This document has passed its expiration date and can no longer be decrypted."
                                DocumentStatus.VIEW_LIMIT_REACHED -> "The sender's maximum view limit has been reached."
                                DocumentStatus.REVOKED -> "The sender has revoked authorized access to this document."
                                DocumentStatus.ACTIVE -> ""
                            },
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Protected with AES-256-GCM. Enter Access Code to derive cryptographic key.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Scan Access Code QR Button
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF001F24))
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Access Code QR", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(0.85f),
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Access Code Entry Input
                        OutlinedTextField(
                            value = enteredCode,
                            onValueChange = {
                                enteredCode = it.uppercase()
                                if (errorMessage != null) errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(0.85f),
                            placeholder = { Text("e.g. X7K9P2", color = TextMuted, textAlign = TextAlign.Center) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = CyanNeon,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (enteredCode.isNotBlank()) attemptDecryption(enteredCode)
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = VaultSurface,
                                unfocusedContainerColor = VaultSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = SecurityRed,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            if (errorMessage!!.contains("not a valid CipherView", ignoreCase = true) ||
                                errorMessage!!.contains("Corrupted", ignoreCase = true) ||
                                errorMessage!!.contains("unencrypted", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        repository.deleteDocument(documentId)
                                        onBack()
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SecurityRed),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecurityRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Remove Invalid File from Vault", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (enteredCode.isNotBlank()) attemptDecryption(enteredCode)
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultSurfaceVariant, contentColor = CyanNeon),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanDark),
                            enabled = enteredCode.isNotBlank()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Decrypt & Open", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Notice on capture resistance
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = VaultSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Capture-Resistant Protection: Android FLAG_SECURE active. Export, screenshot, and recording are prevented inside this viewer.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onScanned = { scannedCode ->
                val clean = AccessCodeGenerator.normalize(scannedCode)
                enteredCode = clean
                showQrScanner = false
                attemptDecryption(clean)
            }
        )
    }
}

