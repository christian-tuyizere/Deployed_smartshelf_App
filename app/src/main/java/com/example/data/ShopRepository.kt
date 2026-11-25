package com.example.data

import android.content.Context
import com.example.data.local.DebtEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.QueuedReceiptEntity
import com.example.data.local.ReceiptRecordEntity
import com.example.data.local.SaleEntity
import com.example.data.local.ShopDao
import com.example.data.local.ShopProfileEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPrefs
import com.example.data.remote.AssistantAiService
import com.example.data.remote.FirestoreSyncManager
import com.example.data.remote.ParsedReceiptLine
import com.example.data.remote.ParsedReceiptResult
import com.example.data.remote.ReceiptAiService
import com.example.util.KigaliTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ProductWithStock(
    val product: ProductEntity,
    val liveStock: Int
) {
    val unitProfit: Long get() = product.normalPrice - product.buyingPrice
    val isLow: Boolean get() = KigaliTime.isLowStock(liveStock, product.minStockLevel)
    val isCritical: Boolean get() = KigaliTime.isCriticalLowStock(liveStock, product.minStockLevel)
}

data class CommodityReportRow(
    val productId: String,
    val productName: String,
    val unit: String,
    val openStock: Int,
    val addedQty: Int,
    val soldQty: Int,
    val adjustedQty: Int,
    val closeStock: Int,
    val profitRwf: Long
)

data class ChartPoint(
    val label: String,
    val salesRwf: Long,
    val profitRwf: Long
)

data class ShopSnapshot(
    val prefs: UserPrefs,
    val profile: ShopProfileEntity?,
    val products: List<ProductWithStock>,
    val movements: List<StockMovementEntity>,
    val sales: List<SaleEntity>,
    val debts: List<DebtEntity>,
    val debtPayments: List<DebtPaymentEntity>,
    val receipts: List<ReceiptRecordEntity>,
    val queuedReceipts: List<QueuedReceiptEntity>
)

data class ReceiptReviewLineInput(
    val matchedProductId: String?, // null = create new product
    val name: String,
    val quantity: Int,
    val unit: String,
    val unitBuyingPrice: Long,
    val normalSellingPrice: Long,
    val minStockLevel: Int = 5
)

class ShopRepository(
    private val dao: ShopDao,
    val prefsRepo: UserPreferencesRepository,
    private val syncManager: FirestoreSyncManager,
    val receiptAiService: ReceiptAiService,
    val assistantAiService: AssistantAiService = AssistantAiService()
) {
    val snapshotFlow: Flow<ShopSnapshot> = combine(
        prefsRepo.prefsFlow,
        dao.observeShopProfile(),
        dao.observeActiveProducts(),
        dao.observeMovements(),
        dao.observeSales()
    ) { prefs, profile, products, movements, sales ->
        val stockMap = movements.groupBy { it.productId }
            .mapValues { (_, list) -> list.sumOf { it.quantityDelta } }
        val productsWithStock = products.map { prod ->
            ProductWithStock(
                product = prod,
                liveStock = stockMap[prod.id] ?: prod.cachedStock
            )
        }
        ShopSnapshot(
            prefs = prefs,
            profile = profile,
            products = productsWithStock,
            movements = movements,
            sales = sales,
            debts = emptyList(),
            debtPayments = emptyList(),
            receipts = emptyList(),
            queuedReceipts = emptyList()
        )
    }.combine(dao.observeDebts()) { snap, debts ->
        snap.copy(debts = debts)
    }.combine(dao.observeDebtPayments()) { snap, payments ->
        snap.copy(debtPayments = payments)
    }.combine(dao.observeReceipts()) { snap, receipts ->
        snap.copy(receipts = receipts)
    }.combine(dao.observeQueuedReceipts()) { snap, queued ->
        snap.copy(queuedReceipts = queued)
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    fun startCloudSync(scope: kotlinx.coroutines.CoroutineScope, uid: String) {
        syncManager.startSync(scope, uid)
    }

    fun stopCloudSync() {
        syncManager.stopSync()
    }

    suspend fun pushPendingSync(): Result<Int> = syncManager.pushPendingLocalChanges()

    suspend fun completeOnboarding(
        shopName: String,
        phone: String,
        languageCode: String,
        pin: String
    ) {
        val now = System.currentTimeMillis()
        val monthKey = KigaliTime.currentMonthKey(now)
        val existing = dao.getShopProfileOnce()
        val profile = ShopProfileEntity(
            id = "local_shop",
            shopName = shopName.trim().ifBlank { "SmartShelf Shop" },
            phone = phone.trim(),
            language = languageCode,
            monthlyReceiptLimit = existing?.monthlyReceiptLimit ?: 60,
            receiptsUsedThisMonth = existing?.receiptsUsedThisMonth ?: 0,
            receiptMonthKey = existing?.receiptMonthKey?.ifBlank { monthKey } ?: monthKey,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
            syncPending = true
        )
        dao.upsertShopProfile(profile)
        prefsRepo.setLanguage(languageCode)
        if (pin.length == 4 && pin.all { it.isDigit() }) {
            prefsRepo.setPinHash(KigaliTime.sha256(pin))
        }
        prefsRepo.setOnboardingCompleted(true)
        syncManager.pushPendingLocalChanges()
    }

    suspend fun updateShopDetails(shopName: String, phone: String, monthlyLimit: Int) {
        val now = System.currentTimeMillis()
        val current = dao.getShopProfileOnce() ?: ShopProfileEntity(
            receiptMonthKey = KigaliTime.currentMonthKey(now)
        )
        dao.upsertShopProfile(
            current.copy(
                shopName = shopName.trim().ifBlank { current.shopName.ifBlank { "SmartShelf" } },
                phone = phone.trim(),
                monthlyReceiptLimit = monthlyLimit.coerceIn(1, 1000),
                updatedAtMillis = now,
                syncPending = true
            )
        )
        syncManager.pushPendingLocalChanges()
    }

    suspend fun setLanguage(code: String) {
        prefsRepo.setLanguage(code)
        val current = dao.getShopProfileOnce() ?: return
        dao.upsertShopProfile(
            current.copy(
                language = code,
                updatedAtMillis = System.currentTimeMillis(),
                syncPending = true
            )
        )
        syncManager.pushPendingLocalChanges()
    }

    suspend fun addProduct(
        name: String,
        unit: String,
        buyingPrice: Long,
        normalPrice: Long,
        startingQty: Int,
        minStockLevel: Int,
        supplierName: String
    ): ProductEntity {
        val now = System.currentTimeMillis()
        val prodId = newId()
        val product = ProductEntity(
            id = prodId,
            name = name.trim().take(120),
            unit = unit,
            buyingPrice = buyingPrice.coerceAtLeast(0L),
            normalPrice = normalPrice.coerceAtLeast(0L),
            minStockLevel = minStockLevel.coerceAtLeast(0),
            cachedStock = startingQty.coerceAtLeast(0),
            supplierName = supplierName.trim().take(120),
            active = true,
            createdAtMillis = now,
            updatedAtMillis = now,
            syncPending = true
        )
        dao.upsertProduct(product)
        if (startingQty != 0) {
            dao.insertMovement(
                StockMovementEntity(
                    id = newId(),
                    productId = prodId,
                    type = "bought",
                    quantityDelta = startingQty,
                    unitCost = buyingPrice.coerceAtLeast(0L),
                    reason = "Initial stock",
                    dateKey = KigaliTime.todayDateKey(now),
                    createdAtMillis = now,
                    syncPending = true
                )
            )
        }
        syncManager.pushPendingLocalChanges()
        return product
    }

    suspend fun updateProduct(
        productId: String,
        name: String,
        unit: String,
        buyingPrice: Long,
        normalPrice: Long,
        minStockLevel: Int,
        supplierName: String
    ) {
        val existing = dao.getProductById(productId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertProduct(
            existing.copy(
                name = name.trim().take(120).ifBlank { existing.name },
                unit = unit,
                buyingPrice = buyingPrice.coerceAtLeast(0L),
                normalPrice = normalPrice.coerceAtLeast(0L),
                minStockLevel = minStockLevel.coerceAtLeast(0),
                supplierName = supplierName.trim().take(120),
                updatedAtMillis = now,
                syncPending = true
            )
        )
        syncManager.pushPendingLocalChanges()
    }

    suspend fun recordSale(
        productWithStock: ProductWithStock,
        quantity: Int,
        unitPrice: Long,
        paymentMethod: String,
        customerName: String,
        customerPhone: String,
        dueDateKey: String
    ): SaleEntity {
        val now = System.currentTimeMillis()
        val saleId = newId()
        val qty = quantity.coerceAtLeast(1)
        val price = unitPrice.coerceAtLeast(0L)
        val cost = productWithStock.product.buyingPrice.coerceAtLeast(0L)
        val total = price * qty
        val profit = (price - cost) * qty
        val dateKey = KigaliTime.todayDateKey(now)
        val monthKey = KigaliTime.currentMonthKey(now)

        val sale = SaleEntity(
            id = saleId,
            productId = productWithStock.product.id,
            productName = productWithStock.product.name,
            quantity = qty,
            unitPrice = price,
            buyingPriceAtSale = cost,
            totalAmount = total,
            profitAmount = profit,
            paymentMethod = paymentMethod,
            status = "completed",
            customerName = customerName.trim().take(120),
            dateKey = dateKey,
            monthKey = monthKey,
            createdAtMillis = now,
            updatedAtMillis = now,
            syncPending = true
        )

        val movement = StockMovementEntity(
            id = newId(),
            productId = productWithStock.product.id,
            type = "sold",
            quantityDelta = -qty,
            unitCost = cost,
            reason = paymentMethod,
            relatedId = saleId,
            dateKey = dateKey,
            createdAtMillis = now,
            syncPending = true
        )

        val updatedProduct = productWithStock.product.copy(
            cachedStock = productWithStock.liveStock - qty,
            updatedAtMillis = now,
            syncPending = true
        )

        val creditDebt = if (paymentMethod == "credit") {
            DebtEntity(
                id = newId(),
                direction = "owed_to_me",
                partyName = customerName.trim().ifBlank { "Customer" }.take(120),
                partyPhone = customerPhone.trim().take(30),
                description = "${qty}x ${productWithStock.product.name}",
                originalAmount = total,
                paidAmount = 0L,
                status = "open",
                dueDateKey = dueDateKey.trim().take(10),
                relatedSaleId = saleId,
                createdAtMillis = now,
                updatedAtMillis = now,
                syncPending = true
            )
        } else null

        dao.recordSaleTransaction(sale, movement, updatedProduct, creditDebt)
        syncManager.pushPendingLocalChanges()
        return sale
    }

    suspend fun cancelSale(saleId: String) {
        val sale = dao.getSaleById(saleId) ?: return
        if (sale.status == "cancelled") return
        val product = dao.getProductById(sale.productId) ?: return
        val now = System.currentTimeMillis()
        val liveStock = dao.getAllMovementsOnce()
            .filter { it.productId == product.id }
            .sumOf { it.quantityDelta }

        val updatedSale = sale.copy(
            status = "cancelled",
            updatedAtMillis = now,
            syncPending = true
        )
        val reverseMovement = StockMovementEntity(
            id = newId(),
            productId = product.id,
            type = "cancelled",
            quantityDelta = sale.quantity,
            unitCost = sale.buyingPriceAtSale,
            reason = "Sale cancelled",
            relatedId = sale.id,
            dateKey = KigaliTime.todayDateKey(now),
            createdAtMillis = now,
            syncPending = true
        )
        val updatedProduct = product.copy(
            cachedStock = liveStock + sale.quantity,
            updatedAtMillis = now,
            syncPending = true
        )
        val linkedDebt = dao.getDebtBySaleId(sale.id)
        val cancelledDebt = if (linkedDebt != null && linkedDebt.status == "open") {
            linkedDebt.copy(status = "cancelled", updatedAtMillis = now, syncPending = true)
        } else null

        dao.cancelSaleTransaction(updatedSale, reverseMovement, updatedProduct, cancelledDebt)
        syncManager.pushPendingLocalChanges()
    }

    suspend fun restockProduct(
        productWithStock: ProductWithStock,
        qtyToAdd: Int,
        newBuyingPrice: Long,
        supplierName: String,
        paidNowRwf: Long,
        dueDateKey: String
    ) {
        val qty = qtyToAdd.coerceAtLeast(1)
        val unitCost = newBuyingPrice.coerceAtLeast(0L)
        val totalCost = unitCost * qty
        val paid = paidNowRwf.coerceIn(0L, totalCost)
        val unpaid = totalCost - paid
        val now = System.currentTimeMillis()
        val supplier = supplierName.trim().ifBlank { productWithStock.product.supplierName }

        val updatedProduct = productWithStock.product.copy(
            buyingPrice = unitCost,
            supplierName = supplier.take(120),
            cachedStock = productWithStock.liveStock + qty,
            updatedAtMillis = now,
            syncPending = true
        )
        val movement = StockMovementEntity(
            id = newId(),
            productId = productWithStock.product.id,
            type = "bought",
            quantityDelta = qty,
            unitCost = unitCost,
            reason = if (supplier.isNotBlank()) "Restock: $supplier" else "Restock",
            dateKey = KigaliTime.todayDateKey(now),
            createdAtMillis = now,
            syncPending = true
        )
        val supplierDebt = if (unpaid > 0L) {
            DebtEntity(
                id = newId(),
                direction = "i_owe",
                partyName = supplier.ifBlank { "Supplier" }.take(120),
                description = "${qty}x ${productWithStock.product.name}",
                originalAmount = unpaid,
                paidAmount = 0L,
                status = "open",
                dueDateKey = dueDateKey.trim().take(10),
                createdAtMillis = now,
                updatedAtMillis = now,
                syncPending = true
            )
        } else null

        dao.restockTransaction(updatedProduct, movement, supplierDebt)
        syncManager.pushPendingLocalChanges()
    }

    suspend fun adjustStock(
        productWithStock: ProductWithStock,
        delta: Int,
        reason: String
    ) {
        if (delta == 0) return
        val now = System.currentTimeMillis()
        val updatedProduct = productWithStock.product.copy(
            cachedStock = productWithStock.liveStock + delta,
            updatedAtMillis = now,
            syncPending = true
        )
        val movement = StockMovementEntity(
            id = newId(),
            productId = productWithStock.product.id,
            type = "adjusted",
            quantityDelta = delta,
            unitCost = productWithStock.product.buyingPrice,
            reason = reason.take(120),
            dateKey = KigaliTime.todayDateKey(now),
            createdAtMillis = now,
            syncPending = true
        )
        dao.adjustStockTransaction(updatedProduct, movement)
        syncManager.pushPendingLocalChanges()
    }

    suspend fun addDebt(
        direction: String,
        partyName: String,
        partyPhone: String,
        description: String,
        amount: Long,
        dueDateKey: String
    ) {
        val now = System.currentTimeMillis()
        val debt = DebtEntity(
            id = newId(),
            direction = direction,
            partyName = partyName.trim().take(120).ifBlank { "Customer" },
            partyPhone = partyPhone.trim().take(30),
            description = description.trim().take(240),
            originalAmount = amount.coerceAtLeast(1L),
            paidAmount = 0L,
            status = "open",
            dueDateKey = dueDateKey.trim().take(10),
            createdAtMillis = now,
            updatedAtMillis = now,
            syncPending = true
        )
        dao.upsertDebt(debt)
        syncManager.pushPendingLocalChanges()
    }

    suspend fun recordDebtPayment(
        debt: DebtEntity,
        paymentAmount: Long,
        method: String
    ) {
        val remaining = (debt.originalAmount - debt.paidAmount).coerceAtLeast(0L)
        val actual = paymentAmount.coerceIn(1L, remaining.coerceAtLeast(1L))
        val newPaid = (debt.paidAmount + actual).coerceAtMost(debt.originalAmount)
        val newStatus = if (newPaid >= debt.originalAmount) "paid" else "open"
        val now = System.currentTimeMillis()

        val updatedDebt = debt.copy(
            paidAmount = newPaid,
            status = newStatus,
            updatedAtMillis = now,
            syncPending = true
        )
        val payment = DebtPaymentEntity(
            id = newId(),
            debtId = debt.id,
            amount = actual,
            method = method,
            dateKey = KigaliTime.todayDateKey(now),
            createdAtMillis = now,
            syncPending = true
        )
        dao.recordDebtPaymentTransaction(updatedDebt, payment)
        syncManager.pushPendingLocalChanges()
    }

    suspend fun queueReceiptPhoto(imagePath: String): QueuedReceiptEntity {
        val item = QueuedReceiptEntity(
            id = newId(),
            imagePath = imagePath,
            status = "queued",
            createdAtMillis = System.currentTimeMillis()
        )
        dao.upsertQueuedReceipt(item)
        return item
    }

    suspend fun processQueuedReceiptsIfOnline() {
        val queued = dao.getQueuedReceiptsOnce()
        for (item in queued) {
            dao.upsertQueuedReceipt(item.copy(status = "parsing"))
            val file = File(item.imagePath)
            if (!file.exists()) {
                dao.deleteQueuedReceipt(item.id)
                continue
            }
            val result = receiptAiService.parseReceiptFile(file)
            result.fold(
                onSuccess = { parsed ->
                    if (parsed.readable) {
                        incrementReceiptCount()
                        dao.upsertQueuedReceipt(
                            item.copy(status = "ready", parsedJson = parsed.rawJson, errorMessage = "")
                        )
                    } else {
                        dao.upsertQueuedReceipt(
                            item.copy(status = "failed", errorMessage = parsed.guidanceMessage)
                        )
                    }
                },
                onFailure = { err ->
                    // Revert to queued on network/transient error so it retries when online
                    dao.upsertQueuedReceipt(
                        item.copy(status = "queued", errorMessage = err.localizedMessage.orEmpty())
                    )
                }
            )
        }
    }

    suspend fun incrementReceiptCount() {
        val now = System.currentTimeMillis()
        val monthKey = KigaliTime.currentMonthKey(now)
        val profile = dao.getShopProfileOnce() ?: ShopProfileEntity(receiptMonthKey = monthKey)
        val used = if (profile.receiptMonthKey == monthKey) profile.receiptsUsedThisMonth + 1 else 1
        dao.upsertShopProfile(
            profile.copy(
                receiptsUsedThisMonth = used,
                receiptMonthKey = monthKey,
                updatedAtMillis = now,
                syncPending = true
            )
        )
    }

    suspend fun deleteQueuedReceipt(id: String) {
        dao.deleteQueuedReceipt(id)
    }

    suspend fun confirmReceiptImport(
        supplierName: String,
        lines: List<ReceiptReviewLineInput>,
        paymentMethod: String,
        paidAmountNow: Long,
        dueDateKey: String,
        queuedReceiptId: String?,
        imagePath: String? = null
    ) {
        val now = System.currentTimeMillis()
        val dateKey = KigaliTime.todayDateKey(now)
        val monthKey = KigaliTime.currentMonthKey(now)
        val supplier = supplierName.trim().take(120)
        val receiptId = newId()
        var grandTotal = 0L
        val validLines = lines.filter { it.quantity > 0 }

        val existingMovements = dao.getAllMovementsOnce()

        for (line in validLines) {
            grandTotal += line.unitBuyingPrice * line.quantity
            val existingProd = line.matchedProductId?.let { dao.getProductById(it) }
            if (existingProd != null) {
                val currentStock = existingMovements
                    .filter { it.productId == existingProd.id }
                    .sumOf { it.quantityDelta }
                dao.upsertProduct(
                    existingProd.copy(
                        buyingPrice = line.unitBuyingPrice.coerceAtLeast(0L),
                        normalPrice = if (line.normalSellingPrice > 0L) line.normalSellingPrice else existingProd.normalPrice,
                        supplierName = if (supplier.isNotBlank()) supplier else existingProd.supplierName,
                        cachedStock = currentStock + line.quantity,
                        updatedAtMillis = now,
                        syncPending = true
                    )
                )
                dao.insertMovement(
                    StockMovementEntity(
                        id = newId(),
                        productId = existingProd.id,
                        type = "bought",
                        quantityDelta = line.quantity,
                        unitCost = line.unitBuyingPrice.coerceAtLeast(0L),
                        reason = if (supplier.isNotBlank()) "Receipt: $supplier" else "Receipt import",
                        relatedId = receiptId,
                        dateKey = dateKey,
                        createdAtMillis = now,
                        syncPending = true
                    )
                )
            } else {
                val newProdId = newId()
                dao.upsertProduct(
                    ProductEntity(
                        id = newProdId,
                        name = line.name.trim().take(120),
                        unit = line.unit,
                        buyingPrice = line.unitBuyingPrice.coerceAtLeast(0L),
                        normalPrice = line.normalSellingPrice.coerceAtLeast(0L),
                        minStockLevel = line.minStockLevel.coerceAtLeast(0),
                        cachedStock = line.quantity,
                        supplierName = supplier,
                        active = true,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        syncPending = true
                    )
                )
                dao.insertMovement(
                    StockMovementEntity(
                        id = newId(),
                        productId = newProdId,
                        type = "bought",
                        quantityDelta = line.quantity,
                        unitCost = line.unitBuyingPrice.coerceAtLeast(0L),
                        reason = if (supplier.isNotBlank()) "Receipt: $supplier" else "Receipt import",
                        relatedId = receiptId,
                        dateKey = dateKey,
                        createdAtMillis = now,
                        syncPending = true
                    )
                )
            }
        }

        val itemsArray = JSONArray().apply {
            validLines.forEach { line ->
                put(JSONObject().apply {
                    put("name", line.name.trim().take(120))
                    put("quantity", line.quantity)
                    put("unit", line.unit)
                    put("unitBuyingPrice", line.unitBuyingPrice.coerceAtLeast(0L))
                    put("normalSellingPrice", line.normalSellingPrice.coerceAtLeast(0L))
                    put("lineTotal", line.unitBuyingPrice.coerceAtLeast(0L) * line.quantity)
                })
            }
        }

        val receipt = ReceiptRecordEntity(
            id = receiptId,
            supplierName = supplier,
            itemCount = validLines.size,
            totalAmount = grandTotal,
            paymentMethod = paymentMethod,
            monthKey = monthKey,
            imagePath = imagePath.orEmpty(),
            itemsJson = itemsArray.toString(),
            createdAtMillis = now,
            syncPending = true
        )
        dao.insertReceipt(receipt)

        if (paymentMethod == "credit" || paidAmountNow < grandTotal) {
            val unpaid = (grandTotal - paidAmountNow.coerceAtLeast(0L)).coerceAtLeast(0L)
            if (unpaid > 0L) {
                dao.upsertDebt(
                    DebtEntity(
                        id = newId(),
                        direction = "i_owe",
                        partyName = supplier.ifBlank { "Wholesale Supplier" },
                        description = "Receipt (${validLines.size} items)",
                        originalAmount = unpaid,
                        paidAmount = 0L,
                        status = "open",
                        dueDateKey = dueDateKey.trim().take(10),
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        syncPending = true
                    )
                )
            }
        }

        if (queuedReceiptId != null) {
            dao.deleteQueuedReceipt(queuedReceiptId)
        }
        syncManager.pushPendingLocalChanges()
    }

    suspend fun deleteAllShopData() {
        dao.deleteAllShopData()
        prefsRepo.resetAllPrefs()
    }

    suspend fun buildJsonBackup(context: Context): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "smartshelf_backup_${KigaliTime.todayDateKey()}.json")
        val root = JSONObject()
        dao.getShopProfileOnce()?.let { p ->
            root.put("shopProfile", JSONObject().apply {
                put("shopName", p.shopName)
                put("phone", p.phone)
                put("language", p.language)
                put("monthlyReceiptLimit", p.monthlyReceiptLimit)
            })
        }
        root.put("products", JSONArray().apply {
            dao.getAllProductsOnce().forEach { prod ->
                put(JSONObject().apply {
                    put("id", prod.id)
                    put("name", prod.name)
                    put("unit", prod.unit)
                    put("buyingPrice", prod.buyingPrice)
                    put("normalPrice", prod.normalPrice)
                    put("minStockLevel", prod.minStockLevel)
                    put("cachedStock", prod.cachedStock)
                    put("supplierName", prod.supplierName)
                })
            }
        })
        root.put("sales", JSONArray().apply {
            dao.getAllSalesOnce().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("productName", s.productName)
                    put("quantity", s.quantity)
                    put("unitPrice", s.unitPrice)
                    put("buyingPriceAtSale", s.buyingPriceAtSale)
                    put("totalAmount", s.totalAmount)
                    put("profitAmount", s.profitAmount)
                    put("paymentMethod", s.paymentMethod)
                    put("status", s.status)
                    put("dateKey", s.dateKey)
                })
            }
        })
        root.put("debts", JSONArray().apply {
            dao.getAllDebtsOnce().forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("direction", d.direction)
                    put("partyName", d.partyName)
                    put("originalAmount", d.originalAmount)
                    put("paidAmount", d.paidAmount)
                    put("status", d.status)
                })
            }
        })
        file.writeText(root.toString(2))
        return file
    }

    suspend fun buildSalesCsv(context: Context): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "smartshelf_sales_${KigaliTime.todayDateKey()}.csv")
        val sb = StringBuilder("Date,Product,Quantity,UnitPrice,BuyingPrice,TotalRWF,ProfitRWF,Method,Status,Customer\n")
        dao.getAllSalesOnce().forEach { s ->
            val safeName = s.productName.replace("\"", "\"\"")
            val safeCust = s.customerName.replace("\"", "\"\"")
            sb.append("${s.dateKey},\"$safeName\",${s.quantity},${s.unitPrice},${s.buyingPriceAtSale},${s.totalAmount},${s.profitAmount},${s.paymentMethod},${s.status},\"$safeCust\"\n")
        }
        file.writeText(sb.toString())
        return file
    }
}
