package com.example.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductWithStock
import com.example.i18n.AppStrings
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoLargeStyle
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellSheet(
    productWithStock: ProductWithStock?,
    allProducts: List<ProductWithStock>,
    strings: AppStrings,
    onOpenPurchaseHistory: ((String) -> Unit)? = null,
    onEditProduct: ((ProductWithStock) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirmSale: (
        item: ProductWithStock,
        quantity: Int,
        unitPrice: Long,
        paymentMethod: String,
        customerName: String,
        customerPhone: String,
        dueDateKey: String
    ) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedItem by remember(productWithStock) {
        mutableStateOf(productWithStock ?: allProducts.firstOrNull())
    }
    var qty by remember { mutableIntStateOf(1) }
    var useNegotiated by remember(selectedItem) {
        mutableStateOf((selectedItem?.product?.normalPrice ?: 0L) <= 0L)
    }
    var customPriceText by remember(selectedItem) {
        mutableStateOf(selectedItem?.product?.normalPrice?.takeIf { it > 0L }?.toString().orEmpty())
    }
    var showBuyingCost by remember(selectedItem) { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf("cash") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var dueDateKey by remember { mutableStateOf("") }
    var lossConfirmed by remember { mutableStateOf(false) }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(strings.sellTitle, style = MaterialTheme.typography.headlineSmall, color = ext.textPrimary)

            if (productWithStock == null && allProducts.isNotEmpty()) {
                var sheetQuery by remember { mutableStateOf("") }
                if (allProducts.size > 4) {
                    OutlinedTextField(
                        value = sheetQuery,
                        onValueChange = { sheetQuery = it },
                        placeholder = { Text(strings.searchPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val visibleProducts = remember(allProducts, sheetQuery) {
                    if (sheetQuery.isBlank()) allProducts
                    else allProducts.filter { it.product.name.contains(sheetQuery.trim(), ignoreCase = true) }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleProducts.forEach { item ->
                        val sel = selectedItem?.product?.id == item.product.id
                        Surface(
                            onClick = {
                                selectedItem = item
                                customPriceText = item.product.normalPrice.takeIf { it > 0L }?.toString().orEmpty()
                                useNegotiated = item.product.normalPrice <= 0L
                                showBuyingCost = false
                                lossConfirmed = false
                            },
                            shape = CircleShape,
                            color = if (sel) ext.accent else ext.elevated,
                            border = BorderStroke(1.dp, if (sel) ext.accent else ext.border)
                        ) {
                            val chipPrice = if (item.product.normalPrice > 0L) {
                                KigaliTime.formatCompactRwf(item.product.normalPrice)
                            } else {
                                strings.setPriceCta
                            }
                            Text(
                                text = "${item.product.name} (${item.liveStock}) • $chipPrice",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            val current = selectedItem
            if (current == null) {
                Text(strings.noProductsTitle, color = ext.textSecondary)
                return@Column
            }

            val needsPriceSetup = current.product.normalPrice <= 0L
            val effectiveUnitPrice = if (useNegotiated || needsPriceSetup) {
                customPriceText.toLongOrNull() ?: 0L
            } else {
                current.product.normalPrice
            }
            val totalAmount = effectiveUnitPrice * qty
            val isLoss = effectiveUnitPrice > 0L && effectiveUnitPrice < current.product.buyingPrice
            val exceedsStock = qty > current.liveStock
            val unitLabel = strings.unitNames[current.product.unit] ?: current.product.unit

            // Product, Selling Price & Stock Header (clean hierarchy, zero collision)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.elevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = current.product.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onEditProduct != null) {
                            IconButton(
                                onClick = { onEditProduct(current) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("sell_edit_product_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = strings.edit,
                                    tint = ext.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (effectiveUnitPrice > 0L) {
                                "${strings.sellingPriceLabel}: ${KigaliTime.formatRwf(effectiveUnitPrice)}"
                            } else {
                                strings.setPriceCta
                            },
                            style = MonoSmallStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = if (effectiveUnitPrice > 0L) ext.accent else ext.warning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${strings.inStockLabel}: ${current.liveStock} $unitLabel",
                            style = MonoSmallStyle.copy(fontWeight = FontWeight.Medium),
                            color = ext.textSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Visible "Reba uko waranguye" toggle button for bargaining estimation
            OutlinedButton(
                onClick = { showBuyingCost = !showBuyingCost },
                shape = CircleShape,
                border = BorderStroke(1.dp, if (showBuyingCost) ext.accent else ext.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sell_check_buying_price_button")
            ) {
                Icon(
                    imageVector = if (showBuyingCost) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = ext.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.checkBuyingPriceCta,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = ext.textPrimary
                )
            }

            AnimatedVisibility(visible = showBuyingCost) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.accentMuted,
                    border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.buyingPriceLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.textSecondary,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${KigaliTime.formatRwf(current.product.buyingPrice)} / 1 $unitLabel",
                                style = MonoMediumStyle.copy(fontWeight = FontWeight.Bold),
                                color = ext.textPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (onOpenPurchaseHistory != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { onOpenPurchaseHistory(current.product.id) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = strings.fullReceiptsLink,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ext.accent,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quantity Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.quantityLabel, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = ext.elevated) {
                        IconButton(onClick = { if (qty > 1) qty-- }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = ext.textPrimary)
                        }
                    }
                    Text(
                        text = qty.toString(),
                        style = MonoLargeStyle,
                        color = ext.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Surface(shape = CircleShape, color = ext.accent) {
                        IconButton(onClick = { qty++ }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Increase",
                                tint = if (ext.isDark) Color(0xFF06130A) else Color.White
                            )
                        }
                    }
                }
            }

            if (exceedsStock) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ext.warningBg,
                    border = BorderStroke(1.dp, ext.warningBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = ext.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.exceedStockWarning, style = MaterialTheme.typography.bodySmall, color = ext.warning)
                    }
                }
            }

            // Negotiated Price Toggle (or forced input if product has no selling price yet)
            if (!needsPriceSetup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.useNegotiatedPrice, style = MaterialTheme.typography.bodyMedium, color = ext.textPrimary)
                    Switch(
                        checked = useNegotiated,
                        onCheckedChange = {
                            useNegotiated = it
                            if (!it) lossConfirmed = false
                        }
                    )
                }
            }

            AnimatedVisibility(visible = useNegotiated || needsPriceSetup) {
                OutlinedTextField(
                    value = customPriceText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) { customPriceText = it; lossConfirmed = false } },
                    label = { Text(if (needsPriceSetup) strings.setPriceCta else strings.negotiatedPriceLabel) },
                    placeholder = { Text(strings.setPriceCta) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sell_price_input")
                )
            }

            if (isLoss) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ext.criticalBg,
                    border = BorderStroke(1.dp, ext.criticalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = lossConfirmed, onCheckedChange = { lossConfirmed = it })
                        Spacer(Modifier.width(4.dp))
                        Text(strings.lossWarning, style = MaterialTheme.typography.bodySmall, color = ext.critical)
                    }
                }
            }

            // Clean Total Amount Bar (no profit estimate shown)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.elevated,
                border = BorderStroke(1.dp, ext.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.totalAmountLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = ext.textSecondary
                    )
                    Text(
                        text = KigaliTime.formatRwf(totalAmount),
                        style = MonoLargeStyle.copy(fontSize = 20.sp),
                        color = ext.textPrimary
                    )
                }
            }

            // Payment Method Chips
            Text(strings.paymentMethodLabel, style = MaterialTheme.typography.labelMedium, color = ext.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cash" to strings.cash, "momo" to strings.momo, "credit" to strings.credit).forEach { (key, lbl) ->
                    val sel = paymentMethod == key
                    Surface(
                        onClick = { paymentMethod = key },
                        shape = CircleShape,
                        color = if (sel) ext.accent else ext.elevated,
                        border = BorderStroke(1.dp, if (sel) ext.accent else ext.border),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sell_method_$key")
                    ) {
                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = paymentMethod == "credit") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text(strings.customerNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text(strings.customerPhoneOptional) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
            }

            Button(
                onClick = {
                    onConfirmSale(
                        current,
                        qty,
                        effectiveUnitPrice,
                        paymentMethod,
                        customerName,
                        customerPhone,
                        dueDateKey
                    )
                },
                enabled = effectiveUnitPrice > 0L && (!isLoss || lossConfirmed) && (paymentMethod != "credit" || customerName.isNotBlank()),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accent,
                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_sale_button")
            ) {
                Text(
                    "${strings.confirmSale} • ${KigaliTime.formatRwf(totalAmount)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductSheet(
    editingProduct: ProductWithStock?,
    existingSuppliers: List<String>,
    strings: AppStrings,
    onOpenPurchaseHistory: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        unit: String,
        buyingPrice: Long,
        normalPrice: Long,
        startingQty: Int,
        minStockLevel: Int,
        supplierName: String
    ) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(editingProduct) { mutableStateOf(editingProduct?.product?.name.orEmpty()) }
    var unit by remember(editingProduct) { mutableStateOf(editingProduct?.product?.unit ?: "piece") }
    var buyingText by remember(editingProduct) { mutableStateOf(editingProduct?.product?.buyingPrice?.takeIf { it > 0L }?.toString().orEmpty()) }
    var normalText by remember(editingProduct) { mutableStateOf(editingProduct?.product?.normalPrice?.takeIf { it > 0L }?.toString().orEmpty()) }
    var startingQtyText by remember { mutableStateOf("10") }
    var minStockText by remember(editingProduct) { mutableStateOf((editingProduct?.product?.minStockLevel ?: 5).toString()) }
    var supplier by remember(editingProduct) { mutableStateOf(editingProduct?.product?.supplierName.orEmpty()) }
    var showMoreOptions by remember { mutableStateOf(false) }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (editingProduct == null) strings.addProductTitle else strings.editProductTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = ext.textPrimary
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.productNameLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input")
            )

            Text(strings.unitLabel, style = MaterialTheme.typography.labelMedium, color = ext.textSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("piece", "kg", "carton", "sack", "bottle", "liter", "pack").forEach { u ->
                    val sel = unit == u
                    Surface(
                        onClick = { unit = u },
                        shape = CircleShape,
                        color = if (sel) ext.accent else ext.elevated,
                        border = BorderStroke(1.dp, if (sel) ext.accent else ext.border)
                    ) {
                        Text(
                            text = strings.unitNames[u] ?: u,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = normalText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) normalText = it },
                    label = { Text(strings.sellingPriceLabel) },
                    placeholder = { Text(strings.setPriceCta) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_normal_input")
                )
                if (editingProduct == null) {
                    OutlinedTextField(
                        value = startingQtyText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) startingQtyText = it },
                        label = { Text(strings.startingQtyLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = buyingText,
                onValueChange = { if (it.all { c -> c.isDigit() }) buyingText = it },
                label = { Text(strings.buyingPriceLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_buying_input")
            )

            // Clean row for More options toggle + Wholesale purchase history link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showMoreOptions = !showMoreOptions }) {
                    Text(strings.moreOptionsLabel, color = ext.textSecondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ext.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (editingProduct != null && onOpenPurchaseHistory != null) {
                    OutlinedButton(
                        onClick = { onOpenPurchaseHistory(editingProduct.product.id) },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ext.border)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.checkBuyingPriceCta,
                            color = ext.accent,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showMoreOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) minStockText = it },
                        label = { Text(strings.minStockLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text(strings.supplierOptionalLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val matchingSuppliers = existingSuppliers.filter {
                        it.isNotBlank() && (supplier.isBlank() || it.contains(supplier, ignoreCase = true)) && it != supplier
                    }.take(5)
                    if (matchingSuppliers.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            matchingSuppliers.forEach { sup ->
                                Surface(
                                    onClick = { supplier = sup },
                                    shape = CircleShape,
                                    color = ext.elevated
                                ) {
                                    Text(
                                        sup,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.textSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val buy = buyingText.toLongOrNull() ?: 0L
                    val norm = normalText.toLongOrNull() ?: 0L
                    val start = startingQtyText.toIntOrNull() ?: 0
                    val min = minStockText.toIntOrNull() ?: 5
                    if (name.isNotBlank()) {
                        onSave(name, unit, buy, norm, start, min, supplier)
                    }
                },
                enabled = name.isNotBlank(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accent,
                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_product_button")
            ) {
                Text(strings.saveProduct, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockSheet(
    targetProduct: ProductWithStock?,
    allProducts: List<ProductWithStock>,
    strings: AppStrings,
    onOpenPurchaseHistory: ((String?) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirmRestock: (
        item: ProductWithStock,
        qtyToAdd: Int,
        newBuyingPrice: Long,
        supplierName: String,
        paidNowRwf: Long,
        dueDateKey: String
    ) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember(targetProduct) { mutableStateOf(targetProduct ?: allProducts.firstOrNull()) }
    var qtyText by remember { mutableStateOf("10") }
    var costText by remember(selected) { mutableStateOf((selected?.product?.buyingPrice ?: 0L).toString()) }
    var supplier by remember(selected) { mutableStateOf(selected?.product?.supplierName.orEmpty()) }
    var paidNowText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var showMoreOptions by remember { mutableStateOf(false) }

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
            Text(strings.restockTitle, style = MaterialTheme.typography.headlineSmall, color = ext.textPrimary)

            if (targetProduct == null && allProducts.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allProducts.forEach { item ->
                        val sel = selected?.product?.id == item.product.id
                        Surface(
                            onClick = {
                                selected = item
                                costText = item.product.buyingPrice.toString()
                                supplier = item.product.supplierName
                            },
                            shape = CircleShape,
                            color = if (sel) ext.accent else ext.elevated
                        ) {
                            Text(
                                item.product.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            val current = selected
            if (current == null) {
                Text(strings.noProductsTitle, color = ext.textSecondary)
                return@Column
            }

            val qty = (qtyText.toIntOrNull() ?: 0).coerceAtLeast(0)
            val cost = costText.toLongOrNull() ?: current.product.buyingPrice
            val totalCost = qty * cost
            val paidNow = paidNowText.toLongOrNull() ?: totalCost

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) qtyText = it },
                    label = { Text(strings.qtyToAddLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) costText = it },
                    label = { Text(strings.buyingPriceLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showMoreOptions = !showMoreOptions }) {
                    Text(strings.moreOptionsLabel, color = ext.textSecondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ext.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onOpenPurchaseHistory != null) {
                    OutlinedButton(
                        onClick = { onOpenPurchaseHistory(current.product.id) },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ext.border)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.checkBuyingPriceCta,
                            color = ext.accent,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showMoreOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text(strings.supplierOptionalLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = paidNowText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) paidNowText = it },
                        label = { Text("${strings.paidAmountNowLabel} (${strings.totalAmountLabel}: ${KigaliTime.formatRwf(totalCost)})") },
                        placeholder = { Text(totalCost.toString()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (paidNow < totalCost) {
                        OutlinedTextField(
                            value = dueDate,
                            onValueChange = { dueDate = it },
                            label = { Text(strings.dueDateOptional) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (qty > 0) {
                        onConfirmRestock(current, qty, cost, supplier, paidNow, dueDate)
                    }
                },
                enabled = qty > 0,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accent,
                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    "${strings.confirmRestock} • ${KigaliTime.formatRwf(totalCost)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockSheet(
    item: ProductWithStock,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirmAdjust: (delta: Int, reason: String) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var deltaText by remember { mutableStateOf("-1") }
    val reasons = listOf(
        "damaged" to strings.reasonDamaged,
        "expired" to strings.reasonExpired,
        "lost" to strings.reasonLost,
        "used_at_home" to strings.reasonUsedAtHome
    )
    var selectedReason by remember { mutableStateOf(reasons.first().first) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ext.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${strings.adjustTitle} — ${item.product.name}", style = MaterialTheme.typography.headlineSmall, color = ext.textPrimary)
            OutlinedTextField(
                value = deltaText,
                onValueChange = { input ->
                    if (input.isEmpty() || input == "-" || input.toIntOrNull() != null) deltaText = input
                },
                label = { Text(strings.adjustDeltaLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(strings.adjustReasonLabel, style = MaterialTheme.typography.labelMedium, color = ext.textSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reasons.forEach { (key, label) ->
                    val sel = selectedReason == key
                    Surface(
                        onClick = { selectedReason = key },
                        shape = CircleShape,
                        color = if (sel) ext.accent else ext.elevated
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val delta = deltaText.toIntOrNull() ?: 0
                    if (delta != 0) onConfirmAdjust(delta, selectedReason)
                },
                enabled = (deltaText.toIntOrNull() ?: 0) != 0,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accent,
                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(strings.confirmAdjust, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockOptionsSheet(
    hasExistingProducts: Boolean,
    strings: AppStrings,
    onSelectReceiptScanner: () -> Unit,
    onSelectNewProduct: () -> Unit,
    onSelectRestockExisting: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ext.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                Text(
                    text = strings.addStock,
                    style = MaterialTheme.typography.headlineSmall,
                    color = ext.textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = ext.textSecondary)
                }
            }

            // Option 1: Add stock using Receipt (AI)
            Surface(
                onClick = onSelectReceiptScanner,
                shape = RoundedCornerShape(18.dp),
                color = ext.elevated,
                border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_stock_option_receipt")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ext.accentMuted,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = strings.addStockOptionReceiptTitle,
                                tint = ext.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.addStockOptionReceiptTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = strings.addStockOptionReceiptSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = ext.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Option 2: Enter stock manually (New product / Restock existing)
            Surface(
                onClick = onSelectNewProduct,
                shape = RoundedCornerShape(18.dp),
                color = ext.elevated,
                border = BorderStroke(1.dp, ext.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_stock_option_manual")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ext.card,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = strings.addStockOptionManualTitle,
                                    tint = ext.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.addStockOptionManualTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ext.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = strings.addStockOptionManualSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary
                            )
                        }
                        if (!hasExistingProducts) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = ext.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (hasExistingProducts) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSelectNewProduct,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ext.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = strings.newProduct,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = ext.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = onSelectRestockExisting,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.accent,
                                    contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = strings.restock,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

