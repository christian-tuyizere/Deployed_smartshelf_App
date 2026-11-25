package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductWithStock
import com.example.data.local.QueuedReceiptEntity
import com.example.notifications.StockNotificationHelper
import com.example.ui.ShopUiState
import com.example.ui.StockFilter
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime

@Composable
fun StockScreen(
    state: ShopUiState,
    filteredProducts: List<ProductWithStock>,
    showAllStock: Boolean,
    onOpenAllStock: (StockFilter) -> Unit,
    onCloseAllStock: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (StockFilter) -> Unit,
    onTapProductToSell: (ProductWithStock?) -> Unit,
    onOpenAddStockOptions: () -> Unit,
    onOpenNewProduct: () -> Unit,
    onOpenAddStock: (ProductWithStock?) -> Unit,
    onOpenShoppingList: () -> Unit,
    onAdjustProduct: (ProductWithStock) -> Unit,
    onEditProduct: (ProductWithStock) -> Unit,
    onReviewQueuedReceipt: (QueuedReceiptEntity) -> Unit,
    onCheckBuyingPrice: (ProductWithStock) -> Unit
) {
    if (!showAllStock) {
        StockOverviewLaunchpad(
            state = state,
            onQuickSell = { onTapProductToSell(null) },
            onOpenAllStock = onOpenAllStock,
            onOpenAddStockOptions = onOpenAddStockOptions,
            onOpenShoppingList = onOpenShoppingList,
            onReviewQueuedReceipt = onReviewQueuedReceipt
        )
    } else {
        BackHandler {
            onCloseAllStock()
        }
        FullStockInventoryView(
            state = state,
            filteredProducts = filteredProducts,
            onBack = onCloseAllStock,
            onSearchChange = onSearchChange,
            onFilterChange = onFilterChange,
            onTapProductToSell = { item -> onTapProductToSell(item) },
            onOpenNewProduct = onOpenNewProduct,
            onOpenAddStock = onOpenAddStock,
            onAdjustProduct = onAdjustProduct,
            onEditProduct = onEditProduct,
            onCheckBuyingPrice = onCheckBuyingPrice
        )
    }
}

@Composable
private fun StockOverviewLaunchpad(
    state: ShopUiState,
    onQuickSell: () -> Unit,
    onOpenAllStock: (StockFilter) -> Unit,
    onOpenAddStockOptions: () -> Unit,
    onOpenShoppingList: () -> Unit,
    onReviewQueuedReceipt: (QueuedReceiptEntity) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ext.canvas)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Optional 1-line Contextual Status Pill (only when an offline receipt finished AI scan)
        if (state.queuedReceiptsReady.isNotEmpty()) {
            val firstReady = state.queuedReceiptsReady.first()
            Surface(
                onClick = { onReviewQueuedReceipt(firstReady) },
                shape = RoundedCornerShape(14.dp),
                color = ext.accentMuted,
                border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.receiptReadyBannerTitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ext.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = strings.receiptReadyReview,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ext.accent
                    )
                }
            }
        }

        // 1. Primary Hero Action Card — Sell / Gurisha
        Surface(
            onClick = onQuickSell,
            shape = RoundedCornerShape(22.dp),
            color = ext.card,
            border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_quick_sell_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ext.accent.copy(alpha = if (ext.isDark) 0.24f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(100f, 40f),
                            radius = 520f
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.accent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = strings.sellCta,
                                tint = if (ext.isDark) Color(0xFF06130A) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = strings.sellCta,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = ext.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = strings.quickSellSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = CircleShape,
                    color = ext.accentMuted,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = ext.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. View Stock Entry Card (Reveals Search, Filters & Full Inventory ONLY when pressed)
        Surface(
            onClick = { onOpenAllStock(StockFilter.ALL) },
            shape = RoundedCornerShape(20.dp),
            color = ext.card,
            border = BorderStroke(1.dp, ext.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("view_all_stock_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ext.elevated,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = strings.viewStockTitle,
                                tint = ext.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = strings.viewStockTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        if (state.products.isEmpty()) {
                            Text(
                                text = strings.viewStockEmptyHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "${state.products.size} ${strings.productsCountSuffix}",
                                style = MonoSmallStyle,
                                color = ext.textSecondary
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = ext.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 3. Clean 2-Card Action Row (Add Stock -> Receipt/Manual chooser + Shopping List)
        Text(
            text = strings.quickActionsHeader,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = ext.textSecondary,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LaunchpadTile(
                icon = Icons.Rounded.AddShoppingCart,
                title = strings.addStock,
                badgeText = null,
                onClick = onOpenAddStockOptions,
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_add_stock")
            )
            LaunchpadTile(
                icon = Icons.Default.PlaylistAddCheck,
                title = strings.shoppingList,
                badgeText = null,
                onClick = onOpenShoppingList,
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_shopping_list")
            )
        }
    }
}

@Composable
private fun LaunchpadTile(
    icon: ImageVector,
    title: String,
    badgeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartShelfColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = ext.card,
        border = BorderStroke(1.dp, ext.border),
        modifier = modifier.height(114.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ext.elevated,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = ext.accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (badgeText != null) {
                    Surface(
                        shape = CircleShape,
                        color = ext.warningBg,
                        border = BorderStroke(1.dp, ext.warningBorder)
                    ) {
                        Text(
                            text = badgeText,
                            style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold),
                            color = ext.warning,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = ext.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FullStockInventoryView(
    state: ShopUiState,
    filteredProducts: List<ProductWithStock>,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (StockFilter) -> Unit,
    onTapProductToSell: (ProductWithStock) -> Unit,
    onOpenNewProduct: () -> Unit,
    onOpenAddStock: (ProductWithStock?) -> Unit,
    onAdjustProduct: (ProductWithStock) -> Unit,
    onEditProduct: (ProductWithStock) -> Unit,
    onCheckBuyingPrice: (ProductWithStock) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    var expandedProductId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ext.canvas),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Inventory Sub-header with Back button & Quick Add
        item(key = "inventory_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stock_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.close,
                            tint = ext.textPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${strings.allStockTitle} (${state.products.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ext.textPrimary
                    )
                }

                Button(
                    onClick = onOpenNewProduct,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentMuted,
                        contentColor = ext.accent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.newProduct, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Fuzzy Search Bar
        item(key = "search_bar") {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchPlaceholder, color = ext.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ext.textSecondary) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = ext.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ext.card,
                    unfocusedContainerColor = ext.card,
                    focusedBorderColor = ext.accent,
                    unfocusedBorderColor = ext.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stock_search_input")
            )
        }

        // Empty State or Clean 3-Column Table (Product | In Stock | Price + Edit)
        if (filteredProducts.isEmpty()) {
            item(key = "empty_stock") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ext.card,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ext.accentMuted,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = ext.accent)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(strings.noProductsTitle, style = MaterialTheme.typography.titleMedium, color = ext.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            strings.noProductsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
                        )
                    }
                }
            }
        } else {
            item(key = "stock_table_header") {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = ext.elevated,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.colProduct,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.textSecondary,
                            modifier = Modifier.weight(1.30f)
                        )
                        Text(
                            text = strings.colInStock,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.textSecondary,
                            modifier = Modifier.weight(0.75f)
                        )
                        Text(
                            text = strings.colPrice,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(0.95f)
                        )
                        Spacer(Modifier.width(40.dp))
                    }
                }
            }

            items(filteredProducts, key = { it.product.id }) { item ->
                StockTableRow(
                    item = item,
                    expanded = expandedProductId == item.product.id,
                    strings = strings,
                    onToggleExpand = {
                        expandedProductId = if (expandedProductId == item.product.id) null else item.product.id
                    },
                    onTapToSell = { onTapProductToSell(item) },
                    onRestock = { onOpenAddStock(item) },
                    onAdjust = { onAdjustProduct(item) },
                    onEdit = { onEditProduct(item) },
                    onCheckBuyingPrice = { onCheckBuyingPrice(item) }
                )
            }
        }
    }
}

@Composable
private fun StockTableRow(
    item: ProductWithStock,
    expanded: Boolean,
    strings: com.example.i18n.AppStrings,
    onToggleExpand: () -> Unit,
    onTapToSell: () -> Unit,
    onRestock: () -> Unit,
    onAdjust: () -> Unit,
    onEdit: () -> Unit,
    onCheckBuyingPrice: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val unitLabel = strings.unitNames[item.product.unit] ?: item.product.unit
    val hasSellingPrice = item.product.normalPrice > 0L

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ext.card,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) ext.accent.copy(alpha = 0.6f) else ext.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpand)
            .testTag("product_card_${item.product.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Product name
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ext.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.30f)
                )

                // Column 2: In stock
                Text(
                    text = "${item.liveStock} $unitLabel",
                    style = MonoSmallStyle.copy(fontWeight = FontWeight.Medium),
                    color = ext.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(0.75f)
                        .padding(horizontal = 4.dp)
                )

                // Column 3: Selling price OR clickable "Add price" / "Shyiraho igiciro"
                Box(
                    modifier = Modifier.weight(0.95f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (hasSellingPrice) {
                        Text(
                            text = KigaliTime.formatRwf(item.product.normalPrice),
                            style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold),
                            color = ext.accent,
                            maxLines = 1
                        )
                    } else {
                        Surface(
                            onClick = onEdit,
                            shape = CircleShape,
                            color = ext.warningBg,
                            border = BorderStroke(1.dp, ext.warningBorder)
                        ) {
                            Text(
                                text = strings.setPriceCta,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ext.warning,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Direct Edit button on every product row to rename / adjust invoice names easily
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_product_${item.product.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = strings.edit,
                        tint = ext.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = ext.border)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallActionPill(
                            icon = Icons.Default.PointOfSale,
                            label = strings.sellCta,
                            onClick = onTapToSell
                        )
                        SmallActionPill(
                            icon = Icons.Default.ReceiptLong,
                            label = strings.checkBuyingPriceCta,
                            highlight = true,
                            onClick = onCheckBuyingPrice
                        )
                        SmallActionPill(
                            icon = Icons.Rounded.AddShoppingCart,
                            label = strings.restock,
                            onClick = onRestock
                        )
                        SmallActionPill(
                            icon = Icons.Default.Tune,
                            label = strings.adjust,
                            onClick = onAdjust
                        )
                        SmallActionPill(
                            icon = Icons.Default.Edit,
                            label = strings.edit,
                            onClick = onEdit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionPill(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) ext.accentMuted else ext.elevated,
        border = if (highlight) BorderStroke(1.dp, ext.accent.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (highlight) ext.accent else ext.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (highlight) ext.accent else ext.textPrimary
            )
        }
    }
}
