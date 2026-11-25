package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.DebtEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ReceiptRecordEntity
import com.example.data.local.SaleEntity
import com.example.data.local.ShopDao
import com.example.data.local.ShopProfileEntity
import com.example.data.local.StockMovementEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreSync"

class FirestoreSyncManager(
    private val db: FirebaseFirestore,
    private val dao: ShopDao,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, dao: ShopDao) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        dao = dao
    )

    private var listenerJobs: List<Job> = emptyList()
    private var activeUid: String? = null

    private fun currentUidOrNull(): String? =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    fun startSync(scope: CoroutineScope, uid: String) {
        if (uid.isBlank() || currentUidOrNull() != uid) return
        if (activeUid == uid && listenerJobs.isNotEmpty()) return
        stopSync()
        activeUid = uid

        scope.launch {
            pushPendingLocalChanges()
        }

        val shopRef = db.collection("shops").document(uid)

        val profileJob = shopRef.snapshots()
            .onEach { snap ->
                if (!snap.exists()) return@onEach
                val remote = snap.toObject(
                    ShopProfileRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@onEach
                val local = dao.getShopProfileOnce()
                val remoteUpdated = remote.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                if (local == null || (!local.syncPending && remoteUpdated >= local.updatedAtMillis)) {
                    dao.upsertShopProfile(
                        ShopProfileEntity(
                            id = "local_shop",
                            shopName = remote.shopName,
                            phone = remote.phone.orEmpty(),
                            language = remote.language,
                            monthlyReceiptLimit = remote.monthlyReceiptLimit,
                            receiptsUsedThisMonth = remote.receiptsUsedThisMonth,
                            receiptMonthKey = remote.receiptMonthKey,
                            createdAtMillis = remote.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            updatedAtMillis = remoteUpdated,
                            syncPending = false
                        )
                    )
                }
            }
            .catch { handleStreamError(it, OperationType.GET, shopRef.path, uid) }
            .launchIn(scope)

        val productsJob = shopRef.collection("products").snapshots()
            .onEach { querySnap ->
                val remotes = querySnap.toObjects(
                    ProductRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val existing = dao.getAllProductsOnce().associateBy { it.id }
                val merged = remotes.mapNotNull { r ->
                    if (r.id.isBlank()) return@mapNotNull null
                    val loc = existing[r.id]
                    val remoteUpdated = r.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    if (loc != null && loc.syncPending && loc.updatedAtMillis > remoteUpdated) {
                        null
                    } else {
                        ProductEntity(
                            id = r.id,
                            name = r.name,
                            unit = r.unit,
                            buyingPrice = r.buyingPrice,
                            normalPrice = r.normalPrice,
                            minStockLevel = r.minStockLevel,
                            cachedStock = r.cachedStock,
                            supplierName = r.supplierName.orEmpty(),
                            active = r.active,
                            createdAtMillis = r.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            updatedAtMillis = remoteUpdated,
                            syncPending = false
                        )
                    }
                }
                if (merged.isNotEmpty()) dao.upsertProducts(merged)
            }
            .catch { handleStreamError(it, OperationType.LIST, "${shopRef.path}/products", uid) }
            .launchIn(scope)

        val movementsJob = shopRef.collection("stockMovements").snapshots()
            .onEach { querySnap ->
                val remotes = querySnap.toObjects(
                    StockMovementRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val items = remotes.mapNotNull { r ->
                    if (r.id.isBlank()) null else StockMovementEntity(
                        id = r.id,
                        productId = r.productId,
                        type = r.type,
                        quantityDelta = r.quantityDelta,
                        unitCost = r.unitCost,
                        reason = r.reason.orEmpty(),
                        relatedId = r.relatedId.orEmpty(),
                        dateKey = r.dateKey,
                        createdAtMillis = r.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        syncPending = false
                    )
                }
                if (items.isNotEmpty()) dao.insertMovements(items)
            }
            .catch { handleStreamError(it, OperationType.LIST, "${shopRef.path}/stockMovements", uid) }
            .launchIn(scope)

        val salesJob = shopRef.collection("sales").snapshots()
            .onEach { querySnap ->
                val remotes = querySnap.toObjects(
                    SaleRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val existing = dao.getAllSalesOnce().associateBy { it.id }
                val merged = remotes.mapNotNull { r ->
                    if (r.id.isBlank()) return@mapNotNull null
                    val loc = existing[r.id]
                    val remoteUpdated = r.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    if (loc != null && loc.syncPending && loc.updatedAtMillis > remoteUpdated) null
                    else SaleEntity(
                        id = r.id,
                        productId = r.productId,
                        productName = r.productName,
                        quantity = r.quantity,
                        unitPrice = r.unitPrice,
                        buyingPriceAtSale = r.buyingPriceAtSale,
                        totalAmount = r.totalAmount,
                        profitAmount = r.profitAmount,
                        paymentMethod = r.paymentMethod,
                        status = r.status,
                        customerName = r.customerName.orEmpty(),
                        dateKey = r.dateKey,
                        monthKey = r.monthKey,
                        createdAtMillis = r.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        updatedAtMillis = remoteUpdated,
                        syncPending = false
                    )
                }
                if (merged.isNotEmpty()) dao.upsertSales(merged)
            }
            .catch { handleStreamError(it, OperationType.LIST, "${shopRef.path}/sales", uid) }
            .launchIn(scope)

        val debtsJob = shopRef.collection("debts").snapshots()
            .onEach { querySnap ->
                val remotes = querySnap.toObjects(
                    DebtRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val existing = dao.getAllDebtsOnce().associateBy { it.id }
                val merged = remotes.mapNotNull { r ->
                    if (r.id.isBlank()) return@mapNotNull null
                    val loc = existing[r.id]
                    val remoteUpdated = r.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    if (loc != null && loc.syncPending && loc.updatedAtMillis > remoteUpdated) null
                    else DebtEntity(
                        id = r.id,
                        direction = r.direction,
                        partyName = r.partyName,
                        partyPhone = r.partyPhone.orEmpty(),
                        description = r.description.orEmpty(),
                        originalAmount = r.originalAmount,
                        paidAmount = r.paidAmount,
                        status = r.status,
                        dueDateKey = r.dueDateKey.orEmpty(),
                        relatedSaleId = r.relatedSaleId.orEmpty(),
                        createdAtMillis = r.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        updatedAtMillis = remoteUpdated,
                        syncPending = false
                    )
                }
                if (merged.isNotEmpty()) dao.upsertDebts(merged)
            }
            .catch { handleStreamError(it, OperationType.LIST, "${shopRef.path}/debts", uid) }
            .launchIn(scope)

        val paymentsJob = shopRef.collection("debtPayments").snapshots()
            .onEach { querySnap ->
                val remotes = querySnap.toObjects(
                    DebtPaymentRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val items = remotes.mapNotNull { r ->
                    if (r.id.isBlank()) null else DebtPaymentEntity(
                        id = r.id,
                        debtId = r.debtId,
                        amount = r.amount,
                        method = r.method,
                        dateKey = r.dateKey,
                        createdAtMillis = r.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        syncPending = false
                    )
                }
                if (items.isNotEmpty()) dao.insertDebtPayments(items)
            }
            .catch { handleStreamError(it, OperationType.LIST, "${shopRef.path}/debtPayments", uid) }
            .launchIn(scope)

        val receiptsJob = shopRef.collection("receipts").snapshots()
            .onEach { querySnap ->
                val remotes = querySnap.toObjects(
                    ReceiptRecordRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val existing = dao.getAllReceiptsOnce().associateBy { it.id }
                val items = remotes.mapNotNull { r ->
                    if (r.id.isBlank()) null else {
                        val loc = existing[r.id]
                        ReceiptRecordEntity(
                            id = r.id,
                            supplierName = r.supplierName.orEmpty(),
                            itemCount = r.itemCount,
                            totalAmount = r.totalAmount,
                            paymentMethod = r.paymentMethod,
                            monthKey = r.monthKey,
                            imagePath = loc?.imagePath.orEmpty(),
                            itemsJson = loc?.itemsJson.orEmpty(),
                            createdAtMillis = r.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            syncPending = false
                        )
                    }
                }
                if (items.isNotEmpty()) dao.insertReceipts(items)
            }
            .catch { handleStreamError(it, OperationType.LIST, "${shopRef.path}/receipts", uid) }
            .launchIn(scope)

        listenerJobs = listOf(profileJob, productsJob, movementsJob, salesJob, debtsJob, paymentsJob, receiptsJob)
    }

    fun stopSync() {
        listenerJobs.forEach { it.cancel() }
        listenerJobs = emptyList()
        activeUid = null
    }

    suspend fun pushPendingLocalChanges(): Result<Int> {
        val uid = currentUidOrNull() ?: return Result.success(0)
        var syncedCount = 0
        val shopRef = db.collection("shops").document(uid)

        try {
            dao.getShopProfileOnce()?.let { profile ->
                if (profile.syncPending) {
                    val snap = shopRef.get().await()
                    if (snap.exists()) {
                        shopRef.update(profile.toFirestoreMap(uid, isCreate = false)).await()
                    } else {
                        shopRef.set(profile.toFirestoreMap(uid, isCreate = true)).await()
                    }
                    dao.upsertShopProfile(profile.copy(syncPending = false))
                    syncedCount++
                }
            }

            for (product in dao.getAllProductsOnce().filter { it.syncPending }) {
                val docRef = shopRef.collection("products").document(product.id)
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(product.toFirestoreMap(uid, isCreate = false)).await()
                } else {
                    docRef.set(product.toFirestoreMap(uid, isCreate = true)).await()
                }
                dao.upsertProduct(product.copy(syncPending = false))
                syncedCount++
            }

            for (mov in dao.getAllMovementsOnce().filter { it.syncPending }) {
                val docRef = shopRef.collection("stockMovements").document(mov.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(mov.toFirestoreCreateMap(uid)).await()
                }
                dao.insertMovement(mov.copy(syncPending = false))
                syncedCount++
            }

            for (sale in dao.getAllSalesOnce().filter { it.syncPending }) {
                val docRef = shopRef.collection("sales").document(sale.id)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    docRef.set(sale.toFirestoreCreateMap(uid)).await()
                } else if (snap.getString("status") == "completed" && sale.status == "cancelled") {
                    docRef.update(
                        mapOf(
                            "status" to "cancelled",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                dao.upsertSale(sale.copy(syncPending = false))
                syncedCount++
            }

            for (debt in dao.getAllDebtsOnce().filter { it.syncPending }) {
                val docRef = shopRef.collection("debts").document(debt.id)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    docRef.set(debt.toFirestoreMap(uid, isCreate = true)).await()
                } else if (snap.getString("status") == "open") {
                    docRef.update(debt.toFirestoreMap(uid, isCreate = false)).await()
                }
                dao.upsertDebt(debt.copy(syncPending = false))
                syncedCount++
            }

            for (pay in dao.getAllDebtPaymentsOnce().filter { it.syncPending }) {
                val docRef = shopRef.collection("debtPayments").document(pay.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(pay.toFirestoreCreateMap(uid)).await()
                }
                dao.insertDebtPayment(pay.copy(syncPending = false))
                syncedCount++
            }

            for (rec in dao.getAllReceiptsOnce().filter { it.syncPending }) {
                val docRef = shopRef.collection("receipts").document(rec.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(rec.toFirestoreCreateMap(uid)).await()
                }
                dao.insertReceipt(rec.copy(syncPending = false))
                syncedCount++
            }

            return Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, shopRef.path)
            return Result.failure(e)
        }
    }

    private fun handleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        Log.w(TAG, "Stream error on $path", firestoreError ?: error)
    }
}
