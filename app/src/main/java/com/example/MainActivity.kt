package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ProductWithStock
import com.example.data.ShopRepository
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.AssistantAction
import com.example.data.remote.FirestoreSyncManager
import com.example.data.remote.ReceiptAiService
import com.example.data.remote.configureAppCheck
import com.example.i18n.AppLanguage
import com.example.notifications.StockNotificationHelper
import com.example.ui.ShopViewModel
import com.example.ui.components.OnboardingScreen
import com.example.ui.components.PinLockScreen
import com.example.ui.components.SmartShelfLogo
import com.example.ui.components.TopProfitBar
import com.example.ui.modals.AddEditProductSheet
import com.example.ui.modals.AddStockOptionsSheet
import com.example.ui.modals.AdjustStockSheet
import com.example.ui.modals.AssistantChatModal
import com.example.ui.modals.PurchaseViewerTab
import com.example.ui.modals.ReceiptScannerModal
import com.example.ui.modals.ReportsModal
import com.example.ui.modals.RestockSheet
import com.example.ui.modals.SavedReceiptsModal
import com.example.ui.modals.SellSheet
import com.example.ui.modals.SettingsModal
import com.example.ui.modals.ShoppingListModal
import com.example.ui.screens.CreditsScreen
import com.example.ui.screens.StockScreen
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.SmartShelfTheme
import com.example.util.KigaliTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            SmartShelfApp()
        }
    }
}

@Composable
fun SmartShelfApp() {
    val context = LocalContext.current
    val viewModel: ShopViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = AppDatabase.getInstance(app)
                val dao = db.shopDao()
                val prefs = UserPreferencesRepository(app)
                val sync = FirestoreSyncManager(app, dao)
                val ai = ReceiptAiService()
                ShopViewModel(ShopRepository(dao, prefs, sync, ai), app)
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assistantChatState by viewModel.assistantChatState.collectAsStateWithLifecycle()

    SmartShelfTheme(darkTheme = state.prefs.darkMode) {
        val ext = LocalSmartShelfColors.current
        if (state.isLoading) {
            Surface(modifier = Modifier.fillMaxSize(), color = ext.canvas) {}
            return@SmartShelfTheme
        }

        if (!state.prefs.onboardingCompleted) {
            OnboardingScreen(
                initialLanguage = AppLanguage.fromCode(state.prefs.language),
                onSelectLanguage = { viewModel.setLanguage(it.code) },
                onCompleteLocal = { name, phone, langCode, pin ->
                    viewModel.completeOnboarding(name, phone, langCode, pin)
                }
            )
            return@SmartShelfTheme
        }

        if (!state.isUnlocked && state.prefs.pinHash.isNotBlank()) {
            PinLockScreen(
                strings = state.strings,
                onVerifyPin = { viewModel.verifyPin(it) }
            )
            return@SmartShelfTheme
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        var showAllStock by remember { mutableStateOf(false) }
        var showSellSheet by remember { mutableStateOf(false) }
        var sellTargetProduct by remember { mutableStateOf<ProductWithStock?>(null) }
        var showAddStockOptions by remember { mutableStateOf(false) }
        var showAddEditProduct by remember { mutableStateOf(false) }
        var editingProduct by remember { mutableStateOf<ProductWithStock?>(null) }
        var showRestockSheet by remember { mutableStateOf(false) }
        var restockTarget by remember { mutableStateOf<ProductWithStock?>(null) }
        var adjustTarget by remember { mutableStateOf<ProductWithStock?>(null) }
        var showShoppingList by remember { mutableStateOf(false) }
        var showReports by remember { mutableStateOf(false) }
        var showReceiptScanner by remember { mutableStateOf(false) }
        var showSavedReceipts by remember { mutableStateOf(false) }
        var savedReceiptsFilterProductId by remember { mutableStateOf<String?>(null) }
        var savedReceiptsInitialTab by remember { mutableStateOf(PurchaseViewerTab.UNIT_COSTS) }
        var showSettings by remember { mutableStateOf(false) }
        var showAssistant by remember { mutableStateOf(false) }

        var askedNotificationPermission by remember { mutableStateOf(false) }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                StockNotificationHelper.scheduleDailyStockReport(context)
            }
        }
        LaunchedEffect(state.prefs.onboardingCompleted, state.prefs.stockAlertsEnabled) {
            if (state.prefs.onboardingCompleted && state.prefs.stockAlertsEnabled) {
                StockNotificationHelper.scheduleDailyStockReport(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !StockNotificationHelper.hasNotificationPermission(context) &&
                    !askedNotificationPermission
                ) {
                    askedNotificationPermission = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        val strings = state.strings
        val filteredProducts = remember(state.products, state.searchQuery, state.stockFilter) {
            viewModel.filteredProducts(state)
        }
        val suppliers = remember(state.products) {
            state.products.map { it.product.supplierName }.filter { it.isNotBlank() }.distinct()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = ext.canvas,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopProfitBar(
                        state = state,
                        onOpenReports = { showReports = true },
                        onOpenSettings = { showSettings = true },
                        onCycleLanguage = {
                            val next = when (AppLanguage.fromCode(state.prefs.language)) {
                                AppLanguage.RW -> AppLanguage.EN
                                AppLanguage.EN -> AppLanguage.FR
                                AppLanguage.FR -> AppLanguage.RW
                            }
                            viewModel.setLanguage(next.code)
                        },
                        onLinkGoogle = { showSettings = true }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = ext.card,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = {
                                if (selectedTab == 0 && showAllStock) {
                                    showAllStock = false
                                    viewModel.setSearchQuery("")
                                    viewModel.setStockFilter(com.example.ui.StockFilter.ALL)
                                }
                                selectedTab = 0
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                    contentDescription = strings.tabStock
                                )
                            },
                            label = { Text(strings.tabStock, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ext.accent,
                                selectedTextColor = ext.accent,
                                indicatorColor = ext.accentMuted,
                                unselectedIconColor = ext.textSecondary,
                                unselectedTextColor = ext.textSecondary
                            ),
                            modifier = Modifier.testTag("nav_stock_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                showAllStock = false
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = strings.tabCredits
                                )
                            },
                            label = { Text(strings.tabCredits, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ext.accent,
                                selectedTextColor = ext.accent,
                                indicatorColor = ext.accentMuted,
                                unselectedIconColor = ext.textSecondary,
                                unselectedTextColor = ext.textSecondary
                            ),
                            modifier = Modifier.testTag("nav_credits_tab")
                        )
                    }
                },
                floatingActionButton = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selectedTab != 0 || showAllStock) {
                            FloatingActionButton(
                                onClick = { showAssistant = true },
                                shape = CircleShape,
                                containerColor = ext.card,
                                contentColor = ext.accent,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("fab_assistant")
                            ) {
                                SmartShelfLogo(showWordmark = false)
                            }
                            ExtendedFloatingActionButton(
                                onClick = {
                                    sellTargetProduct = null
                                    showSellSheet = true
                                },
                                shape = CircleShape,
                                containerColor = ext.accent,
                                contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White,
                                icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                                text = {
                                    Text(
                                        text = strings.sellCta,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                },
                                modifier = Modifier.testTag("fab_sell")
                            )
                        } else {
                            ExtendedFloatingActionButton(
                                onClick = { showAssistant = true },
                                shape = CircleShape,
                                containerColor = ext.card,
                                contentColor = ext.accent,
                                icon = {
                                    SmartShelfLogo(showWordmark = false)
                                },
                                text = {
                                    Text(
                                        text = strings.assistantFabLabel,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ext.textPrimary
                                    )
                                },
                                modifier = Modifier.testTag("fab_assistant")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (selectedTab == 0) {
                        StockScreen(
                            state = state,
                            filteredProducts = filteredProducts,
                            showAllStock = showAllStock,
                            onOpenAllStock = { initialFilter ->
                                viewModel.setStockFilter(initialFilter)
                                showAllStock = true
                            },
                            onCloseAllStock = {
                                showAllStock = false
                                viewModel.setSearchQuery("")
                                viewModel.setStockFilter(com.example.ui.StockFilter.ALL)
                            },
                            onSearchChange = viewModel::setSearchQuery,
                            onFilterChange = viewModel::setStockFilter,
                            onTapProductToSell = { item ->
                                sellTargetProduct = item
                                showSellSheet = true
                            },
                            onOpenAddStockOptions = {
                                showAddStockOptions = true
                            },
                            onOpenNewProduct = {
                                editingProduct = null
                                showAddEditProduct = true
                            },
                            onOpenAddStock = { item ->
                                restockTarget = item
                                showRestockSheet = true
                            },
                            onOpenShoppingList = { showShoppingList = true },
                            onAdjustProduct = { item -> adjustTarget = item },
                            onEditProduct = { item ->
                                editingProduct = item
                                showAddEditProduct = true
                            },
                            onReviewQueuedReceipt = { queued ->
                                viewModel.openQueuedReceiptForReview(queued)
                                showReceiptScanner = true
                            },
                            onCheckBuyingPrice = { item ->
                                savedReceiptsFilterProductId = item.product.id
                                savedReceiptsInitialTab = PurchaseViewerTab.UNIT_COSTS
                                showSavedReceipts = true
                            }
                        )
                    } else {
                        CreditsScreen(
                            state = state,
                            onAddDebt = viewModel::addDebt,
                            onRecordPayment = viewModel::recordDebtPayment
                        )
                    }
                }
            }

            // 7-Second Post-Sale Profit Toast with 1-Tap Undo
            AnimatedVisibility(
                visible = state.saleToast != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp)
            ) {
                state.saleToast?.let { toast ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.card,
                        border = BorderStroke(1.dp, ext.accent),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.saleToastTitle} ${KigaliTime.formatSignedRwf(toast.profitRwf)}",
                                    style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold),
                                    color = if (toast.profitRwf >= 0) ext.accent else ext.critical
                                )
                                Text(
                                    text = toast.productName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textSecondary
                                )
                            }
                            TextButton(
                                onClick = { viewModel.undoSale(toast.saleId) },
                                modifier = Modifier.testTag("undo_sale_button")
                            ) {
                                Text(
                                    strings.undo,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ext.accent
                                )
                            }
                        }
                    }
                }
            }
        }

        // Modals & Bottom Sheets
        if (showAddStockOptions) {
            AddStockOptionsSheet(
                hasExistingProducts = state.products.isNotEmpty(),
                strings = strings,
                onSelectReceiptScanner = {
                    showAddStockOptions = false
                    viewModel.resetReceiptScanState()
                    showReceiptScanner = true
                },
                onSelectNewProduct = {
                    showAddStockOptions = false
                    editingProduct = null
                    showAddEditProduct = true
                },
                onSelectRestockExisting = {
                    showAddStockOptions = false
                    restockTarget = null
                    showRestockSheet = true
                },
                onDismiss = { showAddStockOptions = false }
            )
        }

        if (showSellSheet) {
            SellSheet(
                productWithStock = sellTargetProduct,
                allProducts = state.products,
                strings = strings,
                onOpenPurchaseHistory = { prodId ->
                    showSellSheet = false
                    savedReceiptsFilterProductId = prodId
                    savedReceiptsInitialTab = PurchaseViewerTab.RECEIPTS
                    showSavedReceipts = true
                },
                onEditProduct = { item ->
                    showSellSheet = false
                    editingProduct = item
                    showAddEditProduct = true
                },
                onDismiss = { showSellSheet = false },
                onConfirmSale = { item, qty, price, method, custName, custPhone, due ->
                    viewModel.recordSale(item, qty, price, method, custName, custPhone, due)
                    showSellSheet = false
                }
            )
        }

        if (showAddEditProduct) {
            AddEditProductSheet(
                editingProduct = editingProduct,
                existingSuppliers = suppliers,
                strings = strings,
                onOpenPurchaseHistory = { prodId ->
                    showAddEditProduct = false
                    savedReceiptsFilterProductId = prodId
                    savedReceiptsInitialTab = PurchaseViewerTab.UNIT_COSTS
                    showSavedReceipts = true
                },
                onDismiss = { showAddEditProduct = false },
                onSave = { name, unit, buy, norm, startQty, minStock, sup ->
                    val existing = editingProduct
                    if (existing == null) {
                        viewModel.addProduct(name, unit, buy, norm, startQty, minStock, sup)
                    } else {
                        viewModel.updateProduct(existing.product.id, name, unit, buy, norm, minStock, sup)
                    }
                    showAddEditProduct = false
                }
            )
        }

        if (showRestockSheet) {
            RestockSheet(
                targetProduct = restockTarget,
                allProducts = state.products,
                strings = strings,
                onOpenPurchaseHistory = { prodId ->
                    showRestockSheet = false
                    savedReceiptsFilterProductId = prodId
                    savedReceiptsInitialTab = PurchaseViewerTab.UNIT_COSTS
                    showSavedReceipts = true
                },
                onDismiss = { showRestockSheet = false },
                onConfirmRestock = { item, qty, cost, sup, paidNow, due ->
                    viewModel.restockProduct(item, qty, cost, sup, paidNow, due)
                    showRestockSheet = false
                }
            )
        }

        adjustTarget?.let { target ->
            AdjustStockSheet(
                item = target,
                strings = strings,
                onDismiss = { adjustTarget = null },
                onConfirmAdjust = { delta, reason ->
                    viewModel.adjustStock(target, delta, reason)
                    adjustTarget = null
                }
            )
        }

        if (showShoppingList) {
            ShoppingListModal(
                state = state,
                onDismiss = { showShoppingList = false }
            )
        }

        if (showReports) {
            ReportsModal(
                state = state,
                buildChartSeries = viewModel::buildChartSeries,
                buildCommodityRows = viewModel::buildCommodityRows,
                onCancelSale = viewModel::undoSale,
                onExportPdf = viewModel::exportBusinessReportPdf,
                onDismiss = { showReports = false }
            )
        }

        if (showReceiptScanner) {
            ReceiptScannerModal(
                state = state,
                onScanUri = viewModel::scanReceiptUri,
                onRetryScanPath = viewModel::retryReceiptFile,
                onOpenSavedReceipts = {
                    showReceiptScanner = false
                    savedReceiptsFilterProductId = null
                    savedReceiptsInitialTab = PurchaseViewerTab.RECEIPTS
                    showSavedReceipts = true
                },
                onConfirmImport = { sup, lines, method, paid, due, queuedId, imgPath ->
                    viewModel.confirmReceiptImport(sup, lines, method, paid, due, queuedId, imgPath)
                    showReceiptScanner = false
                },
                onDismiss = { showReceiptScanner = false }
            )
        }

        if (showSavedReceipts) {
            SavedReceiptsModal(
                state = state,
                initialFilterProductId = savedReceiptsFilterProductId,
                initialTab = savedReceiptsInitialTab,
                onDismiss = { showSavedReceipts = false }
            )
        }

        if (showSettings) {
            SettingsModal(
                state = state,
                onSetLanguage = viewModel::setLanguage,
                onSetDarkMode = viewModel::setDarkMode,
                onSetStockAlerts = viewModel::setStockAlertsEnabled,
                onUpdateShopDetails = viewModel::updateShopDetails,
                onSetPin = viewModel::setPin,
                onRemovePin = viewModel::removePin,
                onSyncNow = viewModel::syncNow,
                onBeforeSignOut = { viewModel.stopSyncBeforeSignOut() },
                onExportJson = viewModel::exportJsonBackup,
                onExportCsv = viewModel::exportSalesCsv,
                onDeleteAllData = viewModel::deleteAllShopData,
                onDismiss = { showSettings = false }
            )
        }

        if (showAssistant) {
            AssistantChatModal(
                chatState = assistantChatState,
                strings = strings,
                onSendMessage = viewModel::sendAssistantMessage,
                onRetryLast = viewModel::retryLastAssistantMessage,
                onClearChat = viewModel::clearAssistantChat,
                onTriggerAction = { action ->
                    showAssistant = false
                    when (action) {
                        AssistantAction.SELL -> {
                            sellTargetProduct = null
                            showSellSheet = true
                        }
                        AssistantAction.VIEW_STOCK -> {
                            selectedTab = 0
                            viewModel.setStockFilter(com.example.ui.StockFilter.ALL)
                            showAllStock = true
                        }
                        AssistantAction.ADD_STOCK_OPTIONS -> {
                            showAddStockOptions = true
                        }
                        AssistantAction.SCAN_RECEIPT -> {
                            viewModel.resetReceiptScanState()
                            showReceiptScanner = true
                        }
                        AssistantAction.NEW_PRODUCT -> {
                            editingProduct = null
                            showAddEditProduct = true
                        }
                        AssistantAction.RESTOCK -> {
                            restockTarget = null
                            showRestockSheet = true
                        }
                        AssistantAction.SHOPPING_LIST -> {
                            showShoppingList = true
                        }
                        AssistantAction.SAVED_RECEIPTS -> {
                            savedReceiptsFilterProductId = null
                            savedReceiptsInitialTab = PurchaseViewerTab.RECEIPTS
                            showSavedReceipts = true
                        }
                        AssistantAction.CREDITS -> {
                            selectedTab = 1
                            showAllStock = false
                        }
                        AssistantAction.REPORTS -> {
                            showReports = true
                        }
                        AssistantAction.SETTINGS -> {
                            showSettings = true
                        }
                    }
                },
                onDismiss = { showAssistant = false }
            )
        }
    }
}
