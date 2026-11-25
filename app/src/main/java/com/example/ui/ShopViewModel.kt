package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChartPoint
import com.example.data.CommodityReportRow
import com.example.data.ProductWithStock
import com.example.data.ReceiptReviewLineInput
import com.example.data.ShopRepository
import com.example.data.ShopSnapshot
import com.example.data.local.DebtEntity
import com.example.data.local.ProductEntity
import com.example.data.local.QueuedReceiptEntity
import com.example.data.local.ReceiptRecordEntity
import com.example.data.local.SaleEntity
import com.example.data.local.ShopProfileEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.UserPrefs
import com.example.data.remote.AssistantAction
import com.example.data.remote.AssistantChatTurn
import com.example.data.remote.ParsedReceiptResult
import com.example.data.remote.ReceiptScanTimeoutException
import com.example.data.remote.authStateFlow
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.stringsFor
import com.example.notifications.StockNotificationHelper
import com.example.util.KigaliTime
import com.example.util.ReportPdfGenerator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class StockFilter { ALL, LOW, CRITICAL }
enum class ReportPeriod { TODAY, WEEK, MONTH, ALL }

data class SaleToastData(
    val saleId: String,
    val productName: String,
    val profitRwf: Long
)

sealed interface ReceiptScanUiState {
    data object Idle : ReceiptScanUiState
    data class Parsing(val imagePath: String? = null) : ReceiptScanUiState
    data class QueuedOffline(val message: String) : ReceiptScanUiState
    data class Review(
        val parsed: ParsedReceiptResult,
        val queuedId: String? = null,
        val imagePath: String? = null
    ) : ReceiptScanUiState
    data class ManualFallback(
        val imagePath: String,
        val message: String
    ) : ReceiptScanUiState
    data class Error(val message: String) : ReceiptScanUiState
}

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val actions: List<AssistantAction> = emptyList(),
    val isError: Boolean = false
)

data class AssistantChatUiState(
    val messages: List<AssistantChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val lastUserPrompt: String? = null
)

data class ShopUiState(
    val isLoading: Boolean = true,
    val prefs: UserPrefs = UserPrefs(),
    val strings: AppStrings = stringsFor(AppLanguage.RW),
    val profile: ShopProfileEntity? = null,
    val isUnlocked: Boolean = true,
    val isOnline: Boolean = true,
    val firebaseUserEmail: String? = null,
    val firebaseUserUid: String? = null,
    val pendingSyncCount: Int = 0,
    val products: List<ProductWithStock> = emptyList(),
    val criticalProducts: List<ProductWithStock> = emptyList(),
    val lowStockProducts: List<ProductWithStock> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val movements: List<StockMovementEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val receipts: List<ReceiptRecordEntity> = emptyList(),
    val queuedReceiptsReady: List<QueuedReceiptEntity> = emptyList(),
    val todayProfitRwf: Long = 0L,
    val todaySalesRwf: Long = 0L,
    val pendingOwedToMeRwf: Long = 0L,
    val pendingIOweRwf: Long = 0L,
    val shelfStockValueRwf: Long = 0L,
    val searchQuery: String = "",
    val stockFilter: StockFilter = StockFilter.ALL,
    val saleToast: SaleToastData? = null,
    val receiptScanState: ReceiptScanUiState = ReceiptScanUiState.Idle,
    val statusBannerMessage: String? = null
)

class ShopViewModel(
    private val repository: ShopRepository,
    private val appContext: Context
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val stockFilterFlow = MutableStateFlow(StockFilter.ALL)
    private val isUnlockedFlow = MutableStateFlow(true)
    private val isOnlineFlow = MutableStateFlow(checkOnline())
    private val saleToastFlow = MutableStateFlow<SaleToastData?>(null)
    private val receiptScanFlow = MutableStateFlow<ReceiptScanUiState>(ReceiptScanUiState.Idle)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val _assistantChatState = MutableStateFlow(AssistantChatUiState())
    val assistantChatState: StateFlow<AssistantChatUiState> = _assistantChatState.asStateFlow()
    private val authUserFlow = Firebase.auth.authStateFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Firebase.auth.currentUser)

    private var pinCheckInitialized = false
    private var toastHideJob: Job? = null
    private var assistantJob: Job? = null

    val uiState: StateFlow<ShopUiState> = combine(
        repository.snapshotFlow,
        searchQueryFlow,
        stockFilterFlow,
        isUnlockedFlow,
        isOnlineFlow
    ) { snap, query, filter, unlocked, online ->
        buildState(snap, query, filter, unlocked, online)
    }.combine(authUserFlow) { state, user ->
        state.copy(
            firebaseUserEmail = user?.email,
            firebaseUserUid = user?.uid
        )
    }.combine(saleToastFlow) { state, toast ->
        state.copy(saleToast = toast)
    }.combine(receiptScanFlow) { state, scan ->
        state.copy(receiptScanState = scan)
    }.combine(statusMessageFlow) { state, msg ->
        state.copy(statusBannerMessage = msg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ShopUiState(isLoading = true)
    )

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isOnlineFlow.value = true
            viewModelScope.launch {
                repository.processQueuedReceiptsIfOnline()
                if (Firebase.auth.currentUser != null) {
                    repository.pushPendingSync()
                }
            }
        }

        override fun onLost(network: Network) {
            isOnlineFlow.value = checkOnline()
        }
    }

    init {
        registerConnectivityMonitor()
        viewModelScope.launch {
            authUserFlow.collect { user ->
                val uid = user?.uid
                if (!uid.isNullOrBlank()) {
                    repository.startCloudSync(viewModelScope, uid)
                } else {
                    repository.stopCloudSync()
                }
            }
        }
        viewModelScope.launch {
            repository.snapshotFlow.collect { snap ->
                if (!pinCheckInitialized) {
                    pinCheckInitialized = true
                    if (snap.prefs.onboardingCompleted && snap.prefs.pinHash.isNotBlank()) {
                        isUnlockedFlow.value = false
                    }
                }
                if (snap.prefs.onboardingCompleted && snap.prefs.stockAlertsEnabled) {
                    StockNotificationHelper.scheduleDailyStockReport(appContext)
                } else {
                    StockNotificationHelper.cancelDailyStockReport(appContext)
                }
            }
        }
    }

    private fun buildState(
        snap: ShopSnapshot,
        query: String,
        filter: StockFilter,
        unlocked: Boolean,
        online: Boolean
    ): ShopUiState {
        val lang = AppLanguage.fromCode(snap.prefs.language)
        val strings = stringsFor(lang)
        val todayKey = KigaliTime.todayDateKey()
        val activeCompletedToday = snap.sales.filter { it.status == "completed" && it.dateKey == todayKey }
        val todayProfit = activeCompletedToday.sumOf { it.profitAmount }
        val todaySales = activeCompletedToday.sumOf { it.totalAmount }

        val critical = snap.products.filter { it.isCritical }
        val low = snap.products.filter { it.isLow }
        val shelfValue = snap.products.sumOf { (it.liveStock.coerceAtLeast(0).toLong()) * it.product.buyingPrice }

        val openDebts = snap.debts.filter { it.status == "open" }
        val owedToMe = openDebts.filter { it.direction == "owed_to_me" }.sumOf { (it.originalAmount - it.paidAmount).coerceAtLeast(0L) }
        val iOwe = openDebts.filter { it.direction == "i_owe" }.sumOf { (it.originalAmount - it.paidAmount).coerceAtLeast(0L) }

        val pendingCount =
            (if (snap.profile?.syncPending == true) 1 else 0) +
                snap.products.count { it.product.syncPending } +
                snap.movements.count { it.syncPending } +
                snap.sales.count { it.syncPending } +
                snap.debts.count { it.syncPending } +
                snap.debtPayments.count { it.syncPending } +
                snap.receipts.count { it.syncPending }

        val readyQueued = snap.queuedReceipts.filter { it.status == "ready" }

        return ShopUiState(
            isLoading = false,
            prefs = snap.prefs,
            strings = strings,
            profile = snap.profile,
            isUnlocked = unlocked,
            isOnline = online,
            pendingSyncCount = pendingCount,
            products = snap.products,
            criticalProducts = critical,
            lowStockProducts = low,
            sales = snap.sales,
            movements = snap.movements,
            debts = snap.debts,
            receipts = snap.receipts,
            queuedReceiptsReady = readyQueued,
            todayProfitRwf = todayProfit,
            todaySalesRwf = todaySales,
            pendingOwedToMeRwf = owedToMe,
            pendingIOweRwf = iOwe,
            shelfStockValueRwf = shelfValue,
            searchQuery = query,
            stockFilter = filter
        )
    }

    fun filteredProducts(state: ShopUiState): List<ProductWithStock> {
        val base = when (state.stockFilter) {
            StockFilter.ALL -> state.products
            StockFilter.LOW -> state.lowStockProducts
            StockFilter.CRITICAL -> state.criticalProducts
        }
        val q = state.searchQuery.trim()
        if (q.isEmpty()) return base
        return base.mapNotNull { item ->
            val score = maxOf(
                KigaliTime.fuzzyScore(q, item.product.name),
                KigaliTime.fuzzyScore(q, item.product.supplierName) / 2
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun setSearchQuery(q: String) {
        searchQueryFlow.value = q
    }

    fun setStockFilter(filter: StockFilter) {
        stockFilterFlow.value = filter
    }

    fun verifyPin(pin: String): Boolean {
        val expected = uiState.value.prefs.pinHash
        val ok = expected.isBlank() || KigaliTime.sha256(pin) == expected
        if (ok) isUnlockedFlow.value = true
        return ok
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            if (pin.length == 4 && pin.all { it.isDigit() }) {
                repository.prefsRepo.setPinHash(KigaliTime.sha256(pin))
            }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            repository.prefsRepo.setPinHash("")
            isUnlockedFlow.value = true
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { repository.setLanguage(code) }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch { repository.prefsRepo.setDarkMode(dark) }
    }

    fun setStockAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.prefsRepo.setStockAlertsEnabled(enabled)
            if (enabled) {
                StockNotificationHelper.scheduleDailyStockReport(appContext)
            } else {
                StockNotificationHelper.cancelDailyStockReport(appContext)
            }
        }
    }

    fun completeOnboarding(shopName: String, phone: String, languageCode: String, pin: String) {
        viewModelScope.launch {
            repository.completeOnboarding(shopName, phone, languageCode, pin)
        }
    }

    fun updateShopDetails(shopName: String, phone: String, monthlyLimit: Int) {
        viewModelScope.launch {
            repository.updateShopDetails(shopName, phone, monthlyLimit)
        }
    }

    fun addProduct(
        name: String,
        unit: String,
        buyingPrice: Long,
        normalPrice: Long,
        startingQty: Int,
        minStockLevel: Int,
        supplierName: String
    ) {
        viewModelScope.launch {
            repository.addProduct(name, unit, buyingPrice, normalPrice, startingQty, minStockLevel, supplierName)
        }
    }

    fun updateProduct(
        productId: String,
        name: String,
        unit: String,
        buyingPrice: Long,
        normalPrice: Long,
        minStockLevel: Int,
        supplierName: String
    ) {
        viewModelScope.launch {
            repository.updateProduct(productId, name, unit, buyingPrice, normalPrice, minStockLevel, supplierName)
        }
    }

    fun recordSale(
        productWithStock: ProductWithStock,
        quantity: Int,
        unitPrice: Long,
        paymentMethod: String,
        customerName: String,
        customerPhone: String,
        dueDateKey: String
    ) {
        viewModelScope.launch {
            val sale = repository.recordSale(
                productWithStock, quantity, unitPrice, paymentMethod, customerName, customerPhone, dueDateKey
            )
            toastHideJob?.cancel()
            saleToastFlow.value = SaleToastData(sale.id, sale.productName, sale.profitAmount)
            toastHideJob = viewModelScope.launch {
                delay(7000L)
                saleToastFlow.value = null
            }
        }
    }

    fun undoSale(saleId: String) {
        viewModelScope.launch {
            repository.cancelSale(saleId)
            saleToastFlow.value = null
        }
    }

    fun dismissSaleToast() {
        saleToastFlow.value = null
    }

    fun restockProduct(
        productWithStock: ProductWithStock,
        qtyToAdd: Int,
        newBuyingPrice: Long,
        supplierName: String,
        paidNowRwf: Long,
        dueDateKey: String
    ) {
        viewModelScope.launch {
            repository.restockProduct(productWithStock, qtyToAdd, newBuyingPrice, supplierName, paidNowRwf, dueDateKey)
        }
    }

    fun adjustStock(productWithStock: ProductWithStock, delta: Int, reason: String) {
        viewModelScope.launch {
            repository.adjustStock(productWithStock, delta, reason)
        }
    }

    fun addDebt(
        direction: String,
        partyName: String,
        partyPhone: String,
        description: String,
        amount: Long,
        dueDateKey: String
    ) {
        viewModelScope.launch {
            repository.addDebt(direction, partyName, partyPhone, description, amount, dueDateKey)
        }
    }

    fun recordDebtPayment(debt: DebtEntity, amount: Long, method: String) {
        viewModelScope.launch {
            repository.recordDebtPayment(debt, amount, method)
        }
    }

    fun scanReceiptUri(uri: Uri) {
        viewModelScope.launch {
            receiptScanFlow.value = ReceiptScanUiState.Parsing(imagePath = null)
            val compressed = repository.receiptAiService.compressAndSaveReceiptImage(appContext, uri)
            val file = compressed.getOrElse { err ->
                receiptScanFlow.value = ReceiptScanUiState.Error(err.localizedMessage ?: "Failed to read photo.")
                return@launch
            }
            processCompressedReceiptFile(file)
        }
    }

    fun retryReceiptFile(imagePath: String) {
        viewModelScope.launch {
            val file = File(imagePath)
            if (!file.exists()) {
                receiptScanFlow.value = ReceiptScanUiState.Error(uiState.value.strings.receiptUnreadable)
                return@launch
            }
            processCompressedReceiptFile(file)
        }
    }

    private suspend fun processCompressedReceiptFile(file: File) {
        receiptScanFlow.value = ReceiptScanUiState.Parsing(imagePath = file.absolutePath)
        if (!checkOnline()) {
            repository.queueReceiptPhoto(file.absolutePath)
            receiptScanFlow.value = ReceiptScanUiState.QueuedOffline(uiState.value.strings.queuedOfflineNotice)
            return
        }
        val parsedRes = repository.receiptAiService.parseReceiptFile(file)
        parsedRes.fold(
            onSuccess = { parsed ->
                if (parsed.readable) {
                    repository.incrementReceiptCount()
                    receiptScanFlow.value = ReceiptScanUiState.Review(
                        parsed = parsed,
                        imagePath = file.absolutePath
                    )
                } else {
                    receiptScanFlow.value = ReceiptScanUiState.ManualFallback(
                        imagePath = file.absolutePath,
                        message = parsed.guidanceMessage.ifBlank { uiState.value.strings.receiptUnreadable }
                    )
                }
            },
            onFailure = { err ->
                val fallbackMsg = if (err is ReceiptScanTimeoutException) {
                    uiState.value.strings.scanTimedOutManual
                } else {
                    err.localizedMessage ?: uiState.value.strings.receiptUnreadable
                }
                receiptScanFlow.value = ReceiptScanUiState.ManualFallback(
                    imagePath = file.absolutePath,
                    message = fallbackMsg
                )
            }
        )
    }

    fun openQueuedReceiptForReview(queued: QueuedReceiptEntity) {
        val parsed = repository.receiptAiService.parseJson(queued.parsedJson)
        receiptScanFlow.value = ReceiptScanUiState.Review(
            parsed = parsed,
            queuedId = queued.id,
            imagePath = queued.imagePath
        )
    }

    fun resetReceiptScanState() {
        receiptScanFlow.value = ReceiptScanUiState.Idle
    }

    fun confirmReceiptImport(
        supplierName: String,
        lines: List<ReceiptReviewLineInput>,
        paymentMethod: String,
        paidAmountNow: Long,
        dueDateKey: String,
        queuedReceiptId: String?,
        imagePath: String? = null
    ) {
        viewModelScope.launch {
            repository.confirmReceiptImport(
                supplierName, lines, paymentMethod, paidAmountNow, dueDateKey, queuedReceiptId, imagePath
            )
            receiptScanFlow.value = ReceiptScanUiState.Idle
        }
    }

    fun sendAssistantMessage(rawMessage: String) {
        val trimmed = rawMessage.trim()
        if (trimmed.isBlank() || _assistantChatState.value.isThinking) return

        val snapshotState = uiState.value
        val strings = snapshotState.strings
        val historyTurns = _assistantChatState.value.messages
            .filter { !it.isError }
            .map { AssistantChatTurn(isUser = it.isUser, text = it.text) }

        val userMsg = AssistantChatMessage(isUser = true, text = trimmed)
        _assistantChatState.update {
            it.copy(
                messages = it.messages + userMsg,
                isThinking = true,
                lastUserPrompt = trimmed
            )
        }

        if (!checkOnline()) {
            val offlineMsg = AssistantChatMessage(
                isUser = false,
                text = strings.assistantOfflineError,
                isError = true
            )
            _assistantChatState.update {
                it.copy(
                    messages = it.messages + offlineMsg,
                    isThinking = false
                )
            }
            return
        }

        val inStockCount = snapshotState.products.count { it.liveStock > 0 }
        val openDebtsCount = snapshotState.debts.count { it.status == "open" }
        val summary = buildString {
            append("Shop=${snapshotState.profile?.shopName?.ifBlank { "SmartShelf" } ?: "SmartShelf"}, ")
            append("TotalProducts=${snapshotState.products.size}, ")
            append("InStockProducts=$inStockCount, ")
            append("TodaySales=${KigaliTime.formatRwf(snapshotState.todaySalesRwf)}, ")
            append("TodayProfit=${KigaliTime.formatRwf(snapshotState.todayProfitRwf)}, ")
            append("OpenDebts=$openDebtsCount, ")
            append("SavedReceipts=${snapshotState.receipts.size}")
        }

        assistantJob?.cancel()
        assistantJob = viewModelScope.launch {
            try {
                val result = repository.assistantAiService.askAssistant(
                    userMessage = trimmed,
                    history = historyTurns,
                    shopContextSummary = summary,
                    languageCode = snapshotState.prefs.language
                )
                result.fold(
                    onSuccess = { reply ->
                        val text = reply.text.ifBlank { strings.assistantGenericError }
                        val botMsg = AssistantChatMessage(
                            isUser = false,
                            text = text,
                            actions = reply.actions
                        )
                        _assistantChatState.update {
                            it.copy(
                                messages = it.messages + botMsg,
                                isThinking = false
                            )
                        }
                    },
                    onFailure = { err ->
                        val msg = if (err is TimeoutCancellationException) {
                            strings.assistantTimeoutError
                        } else {
                            err.localizedMessage?.takeIf { !it.contains("gemini", ignoreCase = true) }
                                ?: strings.assistantGenericError
                        }
                        val errMsg = AssistantChatMessage(
                            isUser = false,
                            text = msg,
                            isError = true
                        )
                        _assistantChatState.update {
                            it.copy(
                                messages = it.messages + errMsg,
                                isThinking = false
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun retryLastAssistantMessage() {
        val lastPrompt = _assistantChatState.value.lastUserPrompt ?: return
        if (_assistantChatState.value.isThinking) return
        _assistantChatState.update { state ->
            val trimmedList = state.messages.dropLastWhile { it.isError }
            state.copy(messages = trimmedList)
        }
        val withoutDuplicateUser = _assistantChatState.value.messages.let { list ->
            if (list.lastOrNull()?.isUser == true && list.lastOrNull()?.text == lastPrompt) {
                list.dropLast(1)
            } else list
        }
        _assistantChatState.update { it.copy(messages = withoutDuplicateUser) }
        sendAssistantMessage(lastPrompt)
    }

    fun clearAssistantChat() {
        assistantJob?.cancel()
        _assistantChatState.value = AssistantChatUiState()
    }

    fun syncNow() {
        viewModelScope.launch {
            val res = repository.pushPendingSync()
            statusMessageFlow.value = res.fold(
                onSuccess = { uiState.value.strings.allSaved },
                onFailure = { it.localizedMessage ?: "Sync error" }
            )
            delay(3500L)
            statusMessageFlow.value = null
        }
    }

    fun stopSyncBeforeSignOut() {
        repository.stopCloudSync()
    }

    fun deleteAllShopData() {
        viewModelScope.launch {
            repository.stopCloudSync()
            repository.deleteAllShopData()
            isUnlockedFlow.value = true
        }
    }

    suspend fun exportJsonBackup(): File = repository.buildJsonBackup(appContext)
    suspend fun exportSalesCsv(): File = repository.buildSalesCsv(appContext)
    suspend fun exportBusinessReportPdf(period: ReportPeriod): File = withContext(Dispatchers.IO) {
        val currentState = uiState.value
        val rows = buildCommodityRows(period, currentState)
        ReportPdfGenerator.generateBusinessReportPdf(
            context = appContext,
            state = currentState,
            period = period,
            commodityRows = rows
        )
    }

    fun buildChartSeries(period: ReportPeriod, state: ShopUiState): List<ChartPoint> {
        val daysCount = when (period) {
            ReportPeriod.TODAY -> 7
            ReportPeriod.WEEK -> 7
            ReportPeriod.MONTH -> 14
            ReportPeriod.ALL -> 30
        }
        val keys = KigaliTime.lastNDaysKeys(daysCount)
        val completed = state.sales.filter { it.status == "completed" }
        return keys.map { key ->
            val daySales = completed.filter { it.dateKey == key }
            ChartPoint(
                label = KigaliTime.formatShortDay(key),
                salesRwf = daySales.sumOf { it.totalAmount },
                profitRwf = daySales.sumOf { it.profitAmount }
            )
        }
    }

    fun buildCommodityRows(period: ReportPeriod, state: ShopUiState): List<CommodityReportRow> {
        val today = KigaliTime.todayDateKey()
        val weekStart = KigaliTime.weekStartDateKey()
        val monthKey = KigaliTime.currentMonthKey()

        fun inPeriod(dateKey: String): Boolean = when (period) {
            ReportPeriod.TODAY -> dateKey == today
            ReportPeriod.WEEK -> dateKey >= weekStart
            ReportPeriod.MONTH -> dateKey.startsWith(monthKey)
            ReportPeriod.ALL -> true
        }

        return state.products.mapNotNull { pws ->
            val pMovs = state.movements.filter { it.productId == pws.product.id }
            val periodMovs = pMovs.filter { inPeriod(it.dateKey) }
            val periodSales = state.sales.filter {
                it.productId == pws.product.id && it.status == "completed" && inPeriod(it.dateKey)
            }
            val added = periodMovs.filter { it.type == "bought" }.sumOf { it.quantityDelta }
            val sold = periodSales.sumOf { it.quantity }
            val adj = periodMovs.filter { it.type == "adjusted" }.sumOf { it.quantityDelta }
            val cancelledRestore = periodMovs.filter { it.type == "cancelled" }.sumOf { it.quantityDelta }
            val close = pws.liveStock
            val netInPeriod = periodMovs.sumOf { it.quantityDelta }
            val open = close - netInPeriod
            val profit = periodSales.sumOf { it.profitAmount }
            if (periodMovs.isEmpty() && periodSales.isEmpty() && close == 0) null
            else CommodityReportRow(
                productId = pws.product.id,
                productName = pws.product.name,
                unit = pws.product.unit,
                openStock = open,
                addedQty = added + cancelledRestore,
                soldQty = sold,
                adjustedQty = adj,
                closeStock = close,
                profitRwf = profit
            )
        }
    }

    private fun registerConnectivityMonitor() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(connectivityCallback)
        } catch (_: Exception) {
        }
    }

    private fun checkOnline(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopCloudSync()
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(connectivityCallback)
        } catch (_: Exception) {
        }
    }
}
