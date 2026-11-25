package com.example.ui.modals

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import com.example.data.ProductWithStock
import com.example.data.ReceiptReviewLineInput
import com.example.data.remote.ParsedReceiptResult
import com.example.data.remote.onGoogleSignInClicked
import com.example.data.remote.signOutFromGoogle
import com.example.i18n.AppLanguage
import com.example.notifications.StockNotificationHelper
import com.example.ui.ReceiptScanUiState
import com.example.ui.ShopUiState
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private data class EditableReceiptLine(
    val id: String,
    var name: String,
    var quantityText: String,
    var unit: String,
    var buyingPriceText: String,
    var sellingPriceText: String,
    var matchedProductId: String?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiptScannerModal(
    state: ShopUiState,
    onScanUri: (Uri) -> Unit,
    onRetryScanPath: (String) -> Unit,
    onOpenSavedReceipts: () -> Unit,
    onConfirmImport: (
        supplierName: String,
        lines: List<ReceiptReviewLineInput>,
        paymentMethod: String,
        paidAmountNow: Long,
        dueDateKey: String,
        queuedReceiptId: String?,
        imagePath: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            pendingCameraUri?.let(onScanUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val photoFile = File(dir, "cap_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onScanUri(uri)
    }

    val used = state.profile?.let {
        if (it.receiptMonthKey == KigaliTime.currentMonthKey()) it.receiptsUsedThisMonth else 0
    } ?: 0
    val limit = state.profile?.monthlyReceiptLimit ?: 60

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ext.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.receiptScannerTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = ext.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onOpenSavedReceipts,
                    shape = CircleShape,
                    color = ext.elevated,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.testTag("open_saved_receipts_button")
                ) {
                    Text(
                        text = "${strings.savedReceiptsPillLabel} (${state.receipts.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ext.accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                }
            }

            // Monthly scan quota bar
            Surface(shape = RoundedCornerShape(14.dp), color = ext.elevated, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.monthlyScansUsed,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$used / $limit",
                            style = MonoSmallStyle,
                            color = ext.accent,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / limit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                        color = ext.accent,
                        trackColor = ext.border,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (val scan = state.receiptScanState) {
                is ReceiptScanUiState.Idle, is ReceiptScanUiState.Error, is ReceiptScanUiState.QueuedOffline -> {
                    if (scan is ReceiptScanUiState.Error) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ext.criticalBg,
                            border = BorderStroke(1.dp, ext.criticalBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(scan.message, style = MaterialTheme.typography.bodySmall, color = ext.critical, modifier = Modifier.padding(12.dp))
                        }
                    } else if (scan is ReceiptScanUiState.QueuedOffline) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ext.warningBg,
                            border = BorderStroke(1.dp, ext.warningBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = ext.warning)
                                Spacer(Modifier.width(8.dp))
                                Text(scan.message, style = MaterialTheme.typography.bodySmall, color = ext.warning)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accent,
                                contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("receipt_camera_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.takeReceiptPhoto, style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, ext.border),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("receipt_picker_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ext.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.pickFromGallery, style = MaterialTheme.typography.labelLarge, color = ext.textPrimary)
                        }
                    }
                }

                is ReceiptScanUiState.Parsing -> {
                    if (scan.imagePath != null) {
                        ReceiptPhotoPreviewCard(imagePath = scan.imagePath)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.elevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_scanning_progress")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = ext.accent)
                            Text(
                                text = strings.parsingReceipt,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ext.textPrimary
                            )
                        }
                    }
                }

                is ReceiptScanUiState.ManualFallback -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ext.warningBg,
                        border = BorderStroke(1.dp, ext.warningBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_manual_fallback_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = ext.warning, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = scan.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ext.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                TextButton(onClick = { onRetryScanPath(scan.imagePath) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.retryScan, color = ext.accent, style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text(strings.retakePhoto, color = ext.textPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Text(strings.pickFromGallery, color = ext.textPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Keep uploaded receipt photo clearly visible above the manual entry form
                    ReceiptPhotoPreviewCard(imagePath = scan.imagePath)

                    Text(
                        text = strings.manualEntryWithPhotoHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )

                    ReceiptReviewSection(
                        parsed = ParsedReceiptResult(
                            readable = false,
                            supplierName = "",
                            guidanceMessage = "",
                            items = emptyList(),
                            rawJson = ""
                        ),
                        queuedId = null,
                        imagePath = scan.imagePath,
                        showPhotoPreview = false,
                        startWithBlankLine = true,
                        products = state.products,
                        strings = strings,
                        onConfirmImport = onConfirmImport
                    )
                }

                is ReceiptScanUiState.Review -> {
                    ReceiptReviewSection(
                        parsed = scan.parsed,
                        queuedId = scan.queuedId,
                        imagePath = scan.imagePath,
                        showPhotoPreview = true,
                        startWithBlankLine = false,
                        products = state.products,
                        strings = strings,
                        onConfirmImport = onConfirmImport
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReceiptPhotoPreviewCard(imagePath: String) {
    val ext = LocalSmartShelfColors.current
    var extraRotationDeg by remember(imagePath) { mutableIntStateOf(0) }
    val bitmap = remember(imagePath, extraRotationDeg) {
        runCatching {
            val raw = BitmapFactory.decodeFile(imagePath) ?: return@runCatching null
            val rotated: Bitmap = if (extraRotationDeg != 0) {
                val m = Matrix().apply { postRotate(extraRotationDeg.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }
            rotated.asImageBitmap()
        }.getOrNull()
    }
    var zoomed by remember { mutableStateOf(false) }

    if (bitmap != null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ext.elevated,
            border = BorderStroke(1.dp, ext.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("receipt_photo_preview")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { zoomed = true }
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Uploaded receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { extraRotationDeg = (extraRotationDeg + 90) % 360 },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate receipt 90 degrees",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                    Surface(
                        onClick = { zoomed = true },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom receipt",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                }
            }
        }

        if (zoomed) {
            Dialog(onDismissRequest = { zoomed = false }) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Zoomed receipt photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
                                .clickable { zoomed = false }
                        )
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { extraRotationDeg = (extraRotationDeg + 90) % 360 }) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = ext.textPrimary)
                            }
                            IconButton(onClick = { zoomed = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ext.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptReviewSection(
    parsed: ParsedReceiptResult,
    queuedId: String?,
    imagePath: String?,
    showPhotoPreview: Boolean,
    startWithBlankLine: Boolean,
    products: List<ProductWithStock>,
    strings: com.example.i18n.AppStrings,
    onConfirmImport: (String, List<ReceiptReviewLineInput>, String, Long, String, String?, String?) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    var supplier by remember(parsed) { mutableStateOf(parsed.supplierName) }
    var paymentMethod by remember { mutableStateOf("cash") }
    var paidNowText by remember { mutableStateOf("") }
    var dueDateKey by remember { mutableStateOf("") }

    val editableLines = remember(parsed, startWithBlankLine) {
        mutableStateListOf<EditableReceiptLine>().apply {
            if (parsed.items.isNotEmpty()) {
                parsed.items.forEach { line ->
                    val bestMatch = products.maxByOrNull { KigaliTime.fuzzyScore(line.rawName, it.product.name) }
                    val score = bestMatch?.let { KigaliTime.fuzzyScore(line.rawName, it.product.name) } ?: 0
                    val matched = if (score >= 450) bestMatch else null
                    val existingSellPrice = matched?.product?.normalPrice?.takeIf { it > 0L }?.toString().orEmpty()
                    add(
                        EditableReceiptLine(
                            id = line.id,
                            name = matched?.product?.name ?: line.rawName,
                            quantityText = line.quantity.toString(),
                            unit = matched?.product?.unit ?: line.unit,
                            buyingPriceText = line.unitBuyingPrice.toString(),
                            sellingPriceText = existingSellPrice,
                            matchedProductId = matched?.product?.id
                        )
                    )
                }
            } else if (startWithBlankLine) {
                add(
                    EditableReceiptLine(
                        id = UUID.randomUUID().toString().replace("-", ""),
                        name = "",
                        quantityText = "1",
                        unit = "piece",
                        buyingPriceText = "",
                        sellingPriceText = "",
                        matchedProductId = null
                    )
                )
            }
        }
    }

    val grandTotal = editableLines.sumOf { line ->
        val qty = (line.quantityText.toIntOrNull() ?: 0).coerceAtLeast(0)
        val cost = (line.buyingPriceText.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        qty * cost
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showPhotoPreview && !imagePath.isNullOrBlank()) {
            ReceiptPhotoPreviewCard(imagePath = imagePath)
        }

        OutlinedTextField(
            value = supplier,
            onValueChange = { supplier = it },
            label = { Text(strings.supplierDetected) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        editableLines.forEachIndexed { idx, line ->
            val matchedProd = products.firstOrNull { it.product.id == line.matchedProductId }
            val newCost = line.buyingPriceText.toLongOrNull() ?: 0L
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.elevated,
                border = BorderStroke(1.dp, ext.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (matchedProd != null) matchedProd.product.name else strings.createNewProductTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (matchedProd != null) ext.accent else ext.warning
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (matchedProd != null && matchedProd.product.buyingPrice != newCost) {
                                Text(
                                    text = "${strings.oldPriceLabel}: ${KigaliTime.formatCompactRwf(matchedProd.product.buyingPrice)} -> ${KigaliTime.formatCompactRwf(newCost)}",
                                    style = MonoSmallStyle,
                                    color = ext.warning
                                )
                            }
                            if (editableLines.size > 1) {
                                IconButton(
                                    onClick = { editableLines.removeAt(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove line",
                                        tint = ext.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = line.name,
                        onValueChange = { typed ->
                            val bestMatch = if (typed.trim().length >= 2) {
                                products.maxByOrNull { KigaliTime.fuzzyScore(typed, it.product.name) }
                            } else null
                            val score = bestMatch?.let { KigaliTime.fuzzyScore(typed, it.product.name) } ?: 0
                            val matched = if (score >= 550) bestMatch else null
                            editableLines[idx] = line.copy(
                                name = typed,
                                matchedProductId = matched?.product?.id,
                                unit = matched?.product?.unit ?: line.unit,
                                sellingPriceText = if (line.sellingPriceText.isBlank() && matched != null) {
                                    matched.product.normalPrice.takeIf { it > 0L }?.toString().orEmpty()
                                } else line.sellingPriceText
                            )
                        },
                        label = { Text(strings.productNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = line.quantityText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) editableLines[idx] = line.copy(quantityText = it) },
                            label = { Text(strings.quantityLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedTextField(
                            value = line.buyingPriceText,
                            onValueChange = {
                                if (it.all { c -> c.isDigit() }) {
                                    editableLines[idx] = line.copy(buyingPriceText = it)
                                }
                            },
                            label = { Text(strings.buyingPriceLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = line.sellingPriceText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) editableLines[idx] = line.copy(sellingPriceText = it) },
                            label = { Text(strings.sellingPriceLabel) },
                            placeholder = { Text(strings.setPriceCta) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                editableLines.add(
                    EditableReceiptLine(
                        id = UUID.randomUUID().toString().replace("-", ""),
                        name = "",
                        quantityText = "1",
                        unit = "piece",
                        buyingPriceText = "",
                        sellingPriceText = "",
                        matchedProductId = null
                    )
                )
            },
            shape = CircleShape,
            border = BorderStroke(1.dp, ext.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_manual_receipt_line_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.addReceiptLine, color = ext.accent, style = MaterialTheme.typography.labelLarge)
        }

        Text(strings.paymentMethodLabel, style = MaterialTheme.typography.labelMedium, color = ext.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("cash" to strings.cash, "momo" to strings.momo, "credit" to strings.credit).forEach { (k, l) ->
                val sel = paymentMethod == k
                Surface(
                    onClick = { paymentMethod = k },
                    shape = CircleShape,
                    color = if (sel) ext.accent else ext.elevated,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(l, color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary)
                    }
                }
            }
        }

        if (paymentMethod == "credit") {
            OutlinedTextField(
                value = paidNowText,
                onValueChange = { if (it.all { c -> c.isDigit() }) paidNowText = it },
                label = { Text("${strings.paidAmountNowLabel} (Total: ${KigaliTime.formatRwf(grandTotal)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dueDateKey,
                onValueChange = { dueDateKey = it },
                label = { Text(strings.dueDateOptional) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val validLines = editableLines.filter { it.name.trim().isNotBlank() }
        Button(
            onClick = {
                val reviewLines = validLines.map {
                    ReceiptReviewLineInput(
                        matchedProductId = it.matchedProductId,
                        name = it.name.trim(),
                        quantity = (it.quantityText.toIntOrNull() ?: 1).coerceAtLeast(1),
                        unit = it.unit,
                        unitBuyingPrice = it.buyingPriceText.toLongOrNull() ?: 0L,
                        normalSellingPrice = it.sellingPriceText.toLongOrNull() ?: 0L
                    )
                }
                val paid = if (paymentMethod == "credit") (paidNowText.toLongOrNull() ?: 0L) else grandTotal
                onConfirmImport(supplier, reviewLines, paymentMethod, paid, dueDateKey, queuedId, imagePath)
            },
            enabled = validLines.isNotEmpty(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ext.accent,
                contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("confirm_receipt_import_button")
        ) {
            Text(
                "${strings.confirmReceiptImport} • ${KigaliTime.formatRwf(grandTotal)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    state: ShopUiState,
    onSetLanguage: (String) -> Unit,
    onSetDarkMode: (Boolean) -> Unit,
    onSetStockAlerts: (Boolean) -> Unit,
    onUpdateShopDetails: (String, String, Int) -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onSyncNow: () -> Unit,
    onBeforeSignOut: suspend () -> Unit,
    onExportJson: suspend () -> File,
    onExportCsv: suspend () -> File,
    onDeleteAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var shopName by remember(state.profile) { mutableStateOf(state.profile?.shopName.orEmpty()) }
    var shopPhone by remember(state.profile) { mutableStateOf(state.profile?.phone.orEmpty()) }
    var receiptLimitText by remember(state.profile) { mutableStateOf((state.profile?.monthlyReceiptLimit ?: 60).toString()) }
    var newPin by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var alertFeedback by remember { mutableStateOf<String?>(null) }
    var confirmDeleteStep by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            StockNotificationHelper.scheduleDailyStockReport(context)
            scope.launch {
                val sent = StockNotificationHelper.notifyDailyStockSummaryNow(context, ignoreDisabledFlag = true)
                if (sent) alertFeedback = strings.testAlertSent
            }
        }
    }

    fun shareExportFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ext.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.settingsTitle, style = MaterialTheme.typography.headlineSmall, color = ext.textPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                }
            }

            // Language
            Text(strings.languageLabel, style = MaterialTheme.typography.labelMedium, color = ext.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    val sel = state.prefs.language == lang.code
                    Surface(
                        onClick = { onSetLanguage(lang.code) },
                        shape = CircleShape,
                        color = if (sel) ext.accent else ext.elevated,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                lang.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary
                            )
                        }
                    }
                }
            }

            // Appearance (Obsidian Dark vs Daylight Light)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(strings.appearanceLabel, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                    Text(
                        if (state.prefs.darkMode) strings.darkModeLabel else strings.lightModeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                }
                Switch(
                    checked = state.prefs.darkMode,
                    onCheckedChange = onSetDarkMode,
                    modifier = Modifier.testTag("theme_switch")
                )
            }

            HorizontalDivider(color = ext.border)

            // Critical Low-Stock Alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.stockAlertsTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                    Text(strings.stockAlertsSubtitle, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                }
                Switch(checked = state.prefs.stockAlertsEnabled, onCheckedChange = onSetStockAlerts)
            }
            OutlinedButton(
                onClick = {
                    if (StockNotificationHelper.hasNotificationPermission(context)) {
                        scope.launch {
                            val sent = StockNotificationHelper.notifyDailyStockSummaryNow(context, ignoreDisabledFlag = true)
                            if (sent) alertFeedback = strings.testAlertSent
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                shape = CircleShape,
                border = BorderStroke(1.dp, ext.border)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.sendTestAlert, color = ext.textPrimary)
            }
            if (alertFeedback != null) {
                Text(alertFeedback!!, style = MaterialTheme.typography.bodySmall, color = ext.accent)
            }

            HorizontalDivider(color = ext.border)

            // Google Cloud Sync (Credential Manager)
            Text(strings.cloudSyncTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
            Surface(shape = RoundedCornerShape(14.dp), color = ext.elevated, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (state.firebaseUserEmail != null) "${strings.signedInAs}: ${state.firebaseUserEmail}" else strings.localDeviceOnly,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                    if (authError != null) {
                        Text(authError!!, style = MaterialTheme.typography.bodySmall, color = ext.critical)
                    }
                    if (state.firebaseUserUid == null) {
                        Button(
                            onClick = {
                                authBusy = true
                                authError = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = { authBusy = false },
                                    onAuthError = { msg -> authBusy = false; authError = msg },
                                    scope = scope,
                                    onAuthCancelled = { authBusy = false }
                                )
                            },
                            enabled = !authBusy,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accent,
                                contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_google_signin_button")
                        ) {
                            if (authBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(strings.signInWithGoogle)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onSyncNow,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.accent,
                                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.syncNow)
                            }
                            OutlinedButton(
                                onClick = {
                                    signOutFromGoogle(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        beforeSignOut = onBeforeSignOut
                                    )
                                },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ext.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.signOut, color = ext.textPrimary)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = ext.border)

            // Shop Details
            Text(strings.shopDetailsTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text(strings.shopNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shopPhone,
                    onValueChange = { shopPhone = it },
                    label = { Text(strings.shopPhoneLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = receiptLimitText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) receiptLimitText = it },
                    label = { Text(strings.monthlyReceiptLimitLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    onUpdateShopDetails(shopName, shopPhone, receiptLimitText.toIntOrNull() ?: 60)
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.elevated,
                    contentColor = ext.accent
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.save)
            }

            HorizontalDivider(color = ext.border)

            // Security PIN
            Text(strings.pinSecurityTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
            if (state.prefs.pinHash.isNotBlank()) {
                OutlinedButton(
                    onClick = onRemovePin,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ext.border)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ext.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.removePin, color = ext.textPrimary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text(strings.enter4DigitPin) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newPin.length == 4) {
                                onSetPin(newPin)
                                newPin = ""
                            }
                        },
                        enabled = newPin.length == 4,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.accent,
                            contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                        )
                    ) { Text(strings.setPin) }
                }
            }

            HorizontalDivider(color = ext.border)

            // 3-Step Quick Guide
            Surface(shape = RoundedCornerShape(14.dp), color = ext.elevated, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.quickGuideTitle, style = MaterialTheme.typography.titleSmall, color = ext.accent)
                    Text(strings.guideStep1, style = MaterialTheme.typography.bodySmall, color = ext.textPrimary)
                    Text(strings.guideStep2, style = MaterialTheme.typography.bodySmall, color = ext.textPrimary)
                    Text(strings.guideStep3, style = MaterialTheme.typography.bodySmall, color = ext.textPrimary)
                }
            }

            // Exports (JSON Backup & CSV Sales)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = onExportJson()
                            shareExportFile(f, "application/json")
                        }
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportJsonBackup, style = MaterialTheme.typography.labelMedium, color = ext.textPrimary)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = onExportCsv()
                            shareExportFile(f, "text/csv")
                        }
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportSalesCsv, style = MaterialTheme.typography.labelMedium, color = ext.textPrimary)
                }
            }

            // 2-Step Delete Shop Data
            OutlinedButton(
                onClick = { confirmDeleteStep = true },
                shape = CircleShape,
                border = BorderStroke(1.dp, ext.criticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ext.critical, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.deleteShopData, color = ext.critical)
            }
        }
    }

    if (confirmDeleteStep) {
        AlertDialog(
            onDismissRequest = { confirmDeleteStep = false },
            containerColor = ext.card,
            title = { Text(strings.deleteConfirmTitle, color = ext.textPrimary) },
            text = { Text(strings.deleteConfirmMessage, color = ext.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteStep = false
                        onDeleteAllData()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.critical, contentColor = Color.White)
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteStep = false }) { Text(strings.cancel, color = ext.textSecondary) }
            }
        )
    }
}
