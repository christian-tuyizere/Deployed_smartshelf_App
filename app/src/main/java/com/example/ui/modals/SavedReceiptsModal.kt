package com.example.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ReceiptRecordEntity
import com.example.ui.ShopUiState
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime
import org.json.JSONArray
import java.io.File

enum class PurchaseViewerTab {
    UNIT_COSTS,
    RECEIPTS
}

private data class SavedReceiptItemLine(
    val name: String,
    val quantity: Int,
    val unit: String,
    val unitBuyingPrice: Long,
    val lineTotal: Long
)

private val PACKAGED_VOL_REGEX = Regex("""(?i)(?:^|[\s/\-])\d+(?:[.,]\d+)?\s*(?:L|LT|LTR|ML)(?:\b|$)""")

private fun parseSavedReceiptItems(itemsJson: String): List<SavedReceiptItemLine> {
    if (itemsJson.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(itemsJson)
        (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val name = obj.optString("name", "").trim()
            if (name.isBlank()) return@mapNotNull null
            val qty = obj.optInt("quantity", 1).coerceAtLeast(1)
            val rawUnit = obj.optString("unit", "piece")
            val unit = if (rawUnit == "liter" && PACKAGED_VOL_REGEX.containsMatchIn(name)) "piece" else rawUnit
            val unitPrice = obj.optLong("unitBuyingPrice", 0L).coerceAtLeast(0L)
            val lineTotal = obj.optLong("lineTotal", unitPrice * qty).coerceAtLeast(0L)
            SavedReceiptItemLine(name, qty, unit, unitPrice, lineTotal)
        }
    }.getOrDefault(emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedReceiptsModal(
    state: ShopUiState,
    initialFilterProductId: String? = null,
    initialTab: PurchaseViewerTab = PurchaseViewerTab.UNIT_COSTS,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var activeFilterProductId by remember(initialFilterProductId) { mutableStateOf(initialFilterProductId) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedReceiptId by remember { mutableStateOf<String?>(state.receipts.firstOrNull()?.id) }

    val filterProduct = remember(activeFilterProductId, state.products) {
        activeFilterProductId?.let { id -> state.products.firstOrNull { it.product.id == id } }
    }

    val unitCostProducts = remember(state.products, activeFilterProductId, searchQuery) {
        val base = if (activeFilterProductId != null) {
            state.products.filter { it.product.id == activeFilterProductId }
        } else {
            state.products
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) base
        else base.filter { it.product.name.contains(q, ignoreCase = true) }
    }

    val receiptsToShow = remember(state.receipts, state.movements, activeFilterProductId, filterProduct) {
        if (activeFilterProductId == null) {
            state.receipts
        } else {
            val relatedReceiptIds = state.movements
                .filter { it.type == "bought" && it.productId == activeFilterProductId }
                .mapNotNull { it.relatedId.takeIf(String::isNotBlank) }
                .toSet()
            val prodName = filterProduct?.product?.name.orEmpty()
            state.receipts.filter { rec ->
                rec.id in relatedReceiptIds ||
                    (prodName.isNotBlank() && rec.itemsJson.contains(prodName, ignoreCase = true))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ext.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeTab == PurchaseViewerTab.UNIT_COSTS) {
                            strings.checkBuyingPriceCta
                        } else {
                            strings.savedReceiptsTabLabel
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = ext.textPrimary
                    )
                    if (filterProduct != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = filterProduct.product.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.accent
                            )
                            TextButton(onClick = { activeFilterProductId = null }) {
                                Text(strings.filterAll, style = MaterialTheme.typography.labelMedium, color = ext.textSecondary)
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                }
            }

            // 2-Tab Selector: Unit Buying Costs (Product + Price per 1) | Saved Receipts (Photos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val unitSelected = activeTab == PurchaseViewerTab.UNIT_COSTS
                Surface(
                    onClick = { activeTab = PurchaseViewerTab.UNIT_COSTS },
                    shape = CircleShape,
                    color = if (unitSelected) ext.accent else ext.elevated,
                    border = BorderStroke(1.dp, if (unitSelected) ext.accent else ext.border),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_unit_buying_costs")
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = strings.checkBuyingPriceCta,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (unitSelected && ext.isDark) Color(0xFF06130A) else if (unitSelected) Color.White else ext.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val recSelected = activeTab == PurchaseViewerTab.RECEIPTS
                Surface(
                    onClick = { activeTab = PurchaseViewerTab.RECEIPTS },
                    shape = CircleShape,
                    color = if (recSelected) ext.accent else ext.elevated,
                    border = BorderStroke(1.dp, if (recSelected) ext.accent else ext.border),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_saved_receipts")
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "${strings.savedReceiptsTabLabel} (${receiptsToShow.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (recSelected && ext.isDark) Color(0xFF06130A) else if (recSelected) Color.White else ext.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (activeTab == PurchaseViewerTab.UNIT_COSTS) {
                if (state.products.size > 6 && activeFilterProductId == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(strings.searchPlaceholder, color = ext.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ext.textSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ext.elevated,
                            unfocusedContainerColor = ext.elevated,
                            focusedBorderColor = ext.accent,
                            unfocusedBorderColor = ext.border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (unitCostProducts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.elevated,
                        border = BorderStroke(1.dp, ext.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(strings.noProductsTitle, style = MaterialTheme.typography.titleMedium, color = ext.textPrimary)
                        }
                    }
                } else {
                    // Simple, clean 2-column table: Product | Price / 1 qty
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.elevated,
                        border = BorderStroke(1.dp, ext.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.colProduct,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ext.textSecondary,
                                    modifier = Modifier.weight(1.3f)
                                )
                                Text(
                                    text = strings.unitBuyingPriceCol,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ext.textSecondary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.0f)
                                )
                            }
                            unitCostProducts.forEachIndexed { idx, pws ->
                                HorizontalDivider(color = ext.border)
                                val unitLabel = strings.unitNames[pws.product.unit] ?: pws.product.unit
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = pws.product.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = ext.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    Text(
                                        text = "${KigaliTime.formatRwf(pws.product.buyingPrice)} / 1 $unitLabel",
                                        style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold),
                                        color = ext.accent,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1.0f)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (receiptsToShow.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ext.elevated,
                        border = BorderStroke(1.dp, ext.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ext.accentMuted,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ext.accent)
                                }
                            }
                            Text(
                                text = strings.noSavedReceiptsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = ext.textPrimary
                            )
                            Text(
                                text = strings.noSavedReceiptsSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary
                            )
                        }
                    }
                } else {
                    receiptsToShow.forEach { receipt ->
                        SavedReceiptCard(
                            receipt = receipt,
                            expanded = expandedReceiptId == receipt.id,
                            strings = strings,
                            onToggle = {
                                expandedReceiptId = if (expandedReceiptId == receipt.id) null else receipt.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedReceiptCard(
    receipt: ReceiptRecordEntity,
    expanded: Boolean,
    strings: com.example.i18n.AppStrings,
    onToggle: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val items = remember(receipt.itemsJson) { parseSavedReceiptItems(receipt.itemsJson) }
    val hasPhoto = remember(receipt.imagePath) {
        receipt.imagePath.isNotBlank() && File(receipt.imagePath).exists()
    }
    val dateLabel = "${KigaliTime.formatShortDay(KigaliTime.dateKeyFromMillis(receipt.createdAtMillis))} • ${KigaliTime.formatTimeHm(receipt.createdAtMillis)}"
    val paymentLabel = when (receipt.paymentMethod) {
        "momo" -> strings.momo
        "credit" -> strings.credit
        else -> strings.cash
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = ext.elevated,
        border = BorderStroke(1.dp, if (expanded) ext.accent.copy(alpha = 0.6f) else ext.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_receipt_card_${receipt.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = receipt.supplierName.ifBlank { strings.addFromReceipt },
                        style = MaterialTheme.typography.titleMedium,
                        color = ext.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateLabel • ${receipt.itemCount} • $paymentLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = KigaliTime.formatRwf(receipt.totalAmount),
                        style = MonoMediumStyle.copy(fontWeight = FontWeight.Bold),
                        color = ext.accent
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ext.textSecondary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hasPhoto) {
                        ReceiptPhotoPreviewCard(imagePath = receipt.imagePath)
                    }
                    if (items.isNotEmpty()) {
                        HorizontalDivider(color = ext.border)
                        Text(
                            text = strings.receiptItemsLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.textSecondary
                        )
                        items.forEach { item ->
                            val unitLabel = strings.unitNames[item.unit] ?: item.unit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.name} (${item.quantity} $unitLabel)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${KigaliTime.formatCompactRwf(item.unitBuyingPrice)} × ${item.quantity} = ${KigaliTime.formatRwf(item.lineTotal)}",
                                    style = MonoSmallStyle,
                                    color = ext.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
