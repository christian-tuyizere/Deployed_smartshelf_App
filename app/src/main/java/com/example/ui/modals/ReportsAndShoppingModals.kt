package com.example.ui.modals

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChartPoint
import com.example.data.CommodityReportRow
import com.example.ui.ReportPeriod
import com.example.ui.ShopUiState
import com.example.ui.components.SalesSplineChart
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoLargeStyle
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime
import com.example.util.ReportPdfGenerator
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

private data class ShoppingDraftItem(
    val id: String,
    val name: String,
    val quantityText: String,
    val unitLabel: String,
    val currentStockHint: String? = null,
    val supplierName: String = "",
    val isEditing: Boolean = false
)

private fun normalizeWhatsAppDigits(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return if (digits.length == 10 && digits.startsWith("0")) {
        "250${digits.drop(1)}"
    } else {
        digits
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListModal(
    state: ShopUiState,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val draftItems = remember(state.lowStockProducts) {
        mutableStateListOf<ShoppingDraftItem>().apply {
            state.lowStockProducts.forEach { item ->
                val suggested = ((item.product.minStockLevel * 2).coerceAtLeast(5) - item.liveStock).coerceAtLeast(1)
                val unitName = strings.unitNames[item.product.unit] ?: item.product.unit
                add(
                    ShoppingDraftItem(
                        id = item.product.id,
                        name = item.product.name,
                        quantityText = suggested.toString(),
                        unitLabel = unitName,
                        currentStockHint = "${strings.inStockLabel}: ${item.liveStock} $unitName",
                        supplierName = item.product.supplierName,
                        isEditing = false
                    )
                )
            }
        }
    }

    val knownPhoneContacts = remember(state.debts, state.profile) {
        buildList {
            state.debts
                .filter { it.partyPhone.isNotBlank() }
                .distinctBy { it.partyPhone.filter(Char::isDigit) }
                .forEach { add(it.partyName to it.partyPhone) }
        }
    }

    var recipientPhone by remember {
        mutableStateOf(knownPhoneContacts.firstOrNull()?.second.orEmpty())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ext.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.shoppingListTitle, style = MaterialTheme.typography.headlineSmall, color = ext.textPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                }
            }

            if (draftItems.isEmpty()) {
                Surface(shape = RoundedCornerShape(16.dp), color = ext.elevated, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.nothingToReorder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.textSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                draftItems.forEachIndexed { idx, item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.elevated,
                        border = BorderStroke(1.dp, if (item.isEditing) ext.accent else ext.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (item.isEditing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = item.name,
                                    onValueChange = { draftItems[idx] = item.copy(name = it) },
                                    label = { Text(strings.productNameLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = item.quantityText,
                                        onValueChange = { input ->
                                            if (input.all { it.isDigit() }) {
                                                draftItems[idx] = item.copy(quantityText = input)
                                            }
                                        },
                                        label = { Text(strings.quantityLabel) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = item.unitLabel,
                                        onValueChange = { draftItems[idx] = item.copy(unitLabel = it) },
                                        label = { Text(strings.unitLabel) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { draftItems.removeAt(idx) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ext.critical, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.deleteItemLabel, color = ext.critical, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = { draftItems[idx] = item.copy(isEditing = false) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ext.accent,
                                            contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                                        )
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.doneLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.ifBlank { strings.productNameLabel },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ext.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subParts = listOfNotNull(
                                        item.currentStockHint,
                                        item.supplierName.takeIf { it.isNotBlank() }
                                    )
                                    if (subParts.isNotEmpty()) {
                                        Text(
                                            text = subParts.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(strings.suggestedOrderQty, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                                    Text(
                                        text = "${item.quantityText.ifBlank { "1" }} ${item.unitLabel}",
                                        style = MonoMediumStyle,
                                        color = ext.accent
                                    )
                                }
                                IconButton(
                                    onClick = { draftItems[idx] = item.copy(isEditing = true) },
                                    modifier = Modifier.testTag("shopping_edit_item_$idx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = strings.edit,
                                        tint = ext.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { draftItems.removeAt(idx) },
                                    modifier = Modifier.testTag("shopping_remove_item_$idx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = strings.deleteItemLabel,
                                        tint = ext.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add custom item or quick-add existing stock item
            OutlinedButton(
                onClick = {
                    val defaultUnit = strings.unitNames["piece"] ?: "Piece"
                    draftItems.add(
                        ShoppingDraftItem(
                            id = "custom_${System.currentTimeMillis()}_${draftItems.size}",
                            name = "",
                            quantityText = "1",
                            unitLabel = defaultUnit,
                            isEditing = true
                        )
                    )
                },
                shape = CircleShape,
                border = BorderStroke(1.dp, ext.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_shopping_item_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.addShoppingItemCta, color = ext.accent, style = MaterialTheme.typography.labelLarge)
            }

            val presentIds = draftItems.map { it.id }.toSet()
            val existingNotAdded = remember(state.products, presentIds) {
                state.products.filter { it.product.id !in presentIds }.take(12)
            }
            if (existingNotAdded.isNotEmpty()) {
                Text(
                    text = strings.pickExistingProductHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    existingNotAdded.forEach { prod ->
                        val unitName = strings.unitNames[prod.product.unit] ?: prod.product.unit
                        Surface(
                            onClick = {
                                draftItems.add(
                                    ShoppingDraftItem(
                                        id = prod.product.id,
                                        name = prod.product.name,
                                        quantityText = "5",
                                        unitLabel = unitName,
                                        currentStockHint = "${strings.inStockLabel}: ${prod.liveStock} $unitName",
                                        supplierName = prod.product.supplierName,
                                        isEditing = true
                                    )
                                )
                            },
                            shape = CircleShape,
                            color = ext.elevated,
                            border = BorderStroke(1.dp, ext.border)
                        ) {
                            Text(
                                text = "+ ${prod.product.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = ext.border)

            // Supplier / Recipient Phone Number (for direct WhatsApp or Call)
            OutlinedTextField(
                value = recipientPhone,
                onValueChange = { recipientPhone = it },
                label = { Text(strings.shoppingRecipientPhoneLabel) },
                placeholder = { Text("+250 788 ...") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = ext.textSecondary) },
                trailingIcon = {
                    if (recipientPhone.isNotEmpty()) {
                        IconButton(onClick = { recipientPhone = "" }) {
                            Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shopping_phone_input")
            )

            if (knownPhoneContacts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    knownPhoneContacts.forEach { (contactName, contactPhone) ->
                        val selected = recipientPhone.filter(Char::isDigit) == contactPhone.filter(Char::isDigit)
                        Surface(
                            onClick = { recipientPhone = contactPhone },
                            shape = CircleShape,
                            color = if (selected) ext.accentMuted else ext.elevated,
                            border = BorderStroke(1.dp, if (selected) ext.accent else ext.border)
                        ) {
                            Text(
                                text = "$contactName • $contactPhone",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) ext.accent else ext.textPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            val validItems = draftItems.filter { it.name.trim().isNotBlank() && (it.quantityText.toIntOrNull() ?: 0) > 0 }
            val hasPhone = recipientPhone.any { it.isDigit() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasPhone) {
                    OutlinedButton(
                        onClick = {
                            val dialUri = Uri.parse("tel:${recipientPhone.trim()}")
                            context.startActivity(Intent(Intent.ACTION_DIAL, dialUri))
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ext.border),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("shopping_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = strings.callSupplierCta, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.callSupplierCta, color = ext.textPrimary, style = MaterialTheme.typography.titleSmall)
                    }
                }

                Button(
                    onClick = {
                        val shopName = state.profile?.shopName?.ifBlank { strings.appName } ?: strings.appName
                        val lines = validItems.joinToString("\n") { item ->
                            "- ${item.name.trim()}: ${item.quantityText.trim()} ${item.unitLabel.trim()}"
                        }
                        val msg = strings.whatsappOrderMessageTemplate(shopName, lines)
                        val encoded = URLEncoder.encode(msg, "UTF-8")
                        val waDigits = normalizeWhatsAppDigits(recipientPhone)
                        val url = if (waDigits.isNotBlank()) "https://wa.me/$waDigits?text=$encoded"
                        else "https://wa.me/?text=$encoded"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    enabled = validItems.isNotEmpty(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accent,
                        contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("shopping_whatsapp_button")
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.sendOnWhatsapp,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsModal(
    state: ShopUiState,
    buildChartSeries: (ReportPeriod, ShopUiState) -> List<ChartPoint>,
    buildCommodityRows: (ReportPeriod, ShopUiState) -> List<CommodityReportRow>,
    onCancelSale: (String) -> Unit,
    onExportPdf: suspend (ReportPeriod) -> File,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var period by remember { mutableStateOf(ReportPeriod.TODAY) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var pdfSavedToDownloads by remember { mutableStateOf(false) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val src = generatedPdfFile ?: return@rememberLauncherForActivityResult
        if (destUri != null) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                }
                pdfSavedToDownloads = true
            }
        }
    }

    val today = KigaliTime.todayDateKey()
    val weekStart = KigaliTime.weekStartDateKey()
    val monthKey = KigaliTime.currentMonthKey()

    val periodSales = remember(state.sales, period) {
        state.sales.filter { s ->
            s.status == "completed" && when (period) {
                ReportPeriod.TODAY -> s.dateKey == today
                ReportPeriod.WEEK -> s.dateKey >= weekStart
                ReportPeriod.MONTH -> s.dateKey.startsWith(monthKey)
                ReportPeriod.ALL -> true
            }
        }
    }

    val dailyRows = remember(periodSales) { ReportPdfGenerator.buildDailyEarningsRows(periodSales) }
    val grossProfit = periodSales.sumOf { it.profitAmount }
    val totalSales = periodSales.sumOf { it.totalAmount }
    val cashSales = periodSales.filter { it.paymentMethod == "cash" }.sumOf { it.totalAmount }
    val momoSales = periodSales.filter { it.paymentMethod == "momo" }.sumOf { it.totalAmount }
    val creditSales = periodSales.filter { it.paymentMethod == "credit" }.sumOf { it.totalAmount }
    val chartPoints = remember(state.sales, period) { buildChartSeries(period, state) }
    val commodityRows = remember(state.products, state.movements, state.sales, period) {
        buildCommodityRows(period, state)
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
                Text(strings.reportsTitle, style = MaterialTheme.typography.headlineSmall, color = ext.textPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                }
            }

            // Period Selector Pills (Day / Week / Month / All)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ReportPeriod.TODAY to strings.periodToday,
                    ReportPeriod.WEEK to strings.periodWeek,
                    ReportPeriod.MONTH to strings.periodMonth,
                    ReportPeriod.ALL to strings.periodAll
                ).forEach { (p, label) ->
                    val sel = period == p
                    Surface(
                        onClick = {
                            period = p
                            generatedPdfFile = null
                            pdfSavedToDownloads = false
                        },
                        shape = CircleShape,
                        color = if (sel) ext.accent else ext.elevated,
                        border = BorderStroke(1.dp, if (sel) ext.accent else ext.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary
                            )
                        }
                    }
                }
            }

            // Download Full Business PDF Button (Documented per Date)
            Button(
                onClick = {
                    if (isGeneratingPdf) return@Button
                    isGeneratingPdf = true
                    scope.launch {
                        runCatching {
                            val file = onExportPdf(period)
                            generatedPdfFile = file
                            val downloadedUri = ReportPdfGenerator.savePdfToDownloads(context, file)
                            pdfSavedToDownloads = downloadedUri != null
                            if (downloadedUri == null) {
                                saveAsLauncher.launch(file.name)
                            }
                        }
                        isGeneratingPdf = false
                    }
                },
                enabled = !isGeneratingPdf,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accent,
                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("download_report_pdf_button")
            ) {
                if (isGeneratingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (ext.isDark) Color(0xFF06130A) else Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.generatingPdf, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.downloadPdfCta, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }

            if (generatedPdfFile != null) {
                val readyFile = generatedPdfFile!!
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.accentMuted,
                    border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = ext.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (pdfSavedToDownloads) strings.pdfSavedNotice else readyFile.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ext.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { ReportPdfGenerator.openPdf(context, readyFile) },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ext.accent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ext.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.openPdfCta, color = ext.accent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            OutlinedButton(
                                onClick = { ReportPdfGenerator.sharePdf(context, readyFile) },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ext.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = ext.textPrimary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.sharePdfCta, color = ext.textPrimary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Summary KPI Row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.accentMuted,
                    border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(strings.grossProfitLabel, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            KigaliTime.formatRwf(grossProfit),
                            style = MonoLargeStyle.copy(fontSize = 18.sp),
                            color = if (grossProfit >= 0) ext.accent else ext.critical
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.elevated,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(strings.totalSalesLabel, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            KigaliTime.formatRwf(totalSales),
                            style = MonoLargeStyle.copy(fontSize = 18.sp),
                            color = ext.textPrimary
                        )
                    }
                }
            }

            // Payment Breakdown + Shelf Value
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.elevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PaymentStat(strings.cash, cashSales)
                    PaymentStat(strings.momo, momoSales)
                    PaymentStat(strings.credit, creditSales)
                    PaymentStat(strings.shelfStockValueLabel, state.shelfStockValueRwf)
                }
            }

            // Sales Over Time Spline Chart
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.canvas,
                border = BorderStroke(1.dp, ext.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(strings.salesOverTimeLabel, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    SalesSplineChart(points = chartPoints)
                }
            }

            // Date-by-Date Earnings Breakdown
            if (dailyRows.isNotEmpty()) {
                Text(strings.dailyBreakdownTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.canvas,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.pdfDateCol, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.weight(1.1f))
                            Text(strings.pdfTransactionsCol, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.weight(0.55f))
                            Text(strings.totalSalesLabel, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.weight(1f))
                            Text(strings.colProfit, style = MaterialTheme.typography.labelSmall, color = ext.accent, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = ext.border)
                        dailyRows.forEach { d ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(d.dateKey, style = MonoSmallStyle, color = ext.textPrimary, modifier = Modifier.weight(1.1f))
                                Text("${d.salesCount}", style = MonoSmallStyle, color = ext.textSecondary, modifier = Modifier.weight(0.55f))
                                Text(KigaliTime.formatCompactRwf(d.totalSalesRwf), style = MonoSmallStyle, color = ext.textPrimary, modifier = Modifier.weight(1f))
                                Text(
                                    KigaliTime.formatCompactRwf(d.profitRwf),
                                    style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold),
                                    color = if (d.profitRwf >= 0) ext.accent else ext.critical,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Stock Status Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.elevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.stockStatusLabel, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                    StatusCountRow(strings.totalProductsLabel, state.products.size, ext.textPrimary)
                    StatusCountRow(strings.outOfStockLabel, state.products.count { it.liveStock <= 0 }, ext.textSecondary)
                }
            }

            // Commodity Movement & Profit Table
            if (commodityRows.isNotEmpty()) {
                Text(strings.commodityTableTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.canvas,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(strings.colProduct, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.width(110.dp))
                            Text(strings.colOpen, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                            Text(strings.colAdded, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                            Text(strings.colSold, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                            Text(strings.colAdj, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                            Text(strings.colClose, style = MaterialTheme.typography.labelSmall, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                            Text(strings.colProfit, style = MaterialTheme.typography.labelSmall, color = ext.accent, modifier = Modifier.width(86.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = ext.border)
                        commodityRows.forEach { r ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r.productName, style = MaterialTheme.typography.bodySmall, color = ext.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                                Text("${r.openStock}", style = MonoSmallStyle, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                                Text("${r.addedQty}", style = MonoSmallStyle, color = ext.textPrimary, modifier = Modifier.width(42.dp))
                                Text("${r.soldQty}", style = MonoSmallStyle, color = ext.textPrimary, modifier = Modifier.width(42.dp))
                                Text("${r.adjustedQty}", style = MonoSmallStyle, color = ext.textSecondary, modifier = Modifier.width(42.dp))
                                Text("${r.closeStock}", style = MonoSmallStyle, color = ext.textPrimary, modifier = Modifier.width(42.dp))
                                Text(KigaliTime.formatCompactRwf(r.profitRwf), style = MonoSmallStyle, color = if (r.profitRwf >= 0) ext.accent else ext.critical, modifier = Modifier.width(86.dp))
                            }
                        }
                    }
                }
            }

            // Recent Sales with Cancel Sale
            if (state.sales.isNotEmpty()) {
                Text(strings.recentSalesTitle, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                state.sales.take(15).forEach { sale ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ext.elevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${sale.quantity}x ${sale.productName}", style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                                Text(
                                    "${sale.dateKey} ${KigaliTime.formatTimeHm(sale.createdAtMillis)} • ${sale.paymentMethod.uppercase()} • ${KigaliTime.formatRwf(sale.totalAmount)}",
                                    style = MonoSmallStyle,
                                    color = ext.textSecondary
                                )
                            }
                            if (sale.status == "cancelled") {
                                Text(strings.cancelledBadge, style = MaterialTheme.typography.labelSmall, color = ext.critical)
                            } else {
                                OutlinedButton(
                                    onClick = { onCancelSale(sale.id) },
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, ext.border)
                                ) {
                                    Text(strings.cancelSale, style = MaterialTheme.typography.labelSmall, color = ext.critical)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentStat(label: String, amount: Long) {
    val ext = LocalSmartShelfColors.current
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary, maxLines = 1)
        Text(KigaliTime.formatCompactRwf(amount), style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold), color = ext.textPrimary)
    }
}

@Composable
private fun StatusCountRow(label: String, count: Int, valueColor: Color) {
    val ext = LocalSmartShelfColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
        Text("$count", style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}
