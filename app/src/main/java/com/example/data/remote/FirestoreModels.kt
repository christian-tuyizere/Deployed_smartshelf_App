package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.DebtEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ReceiptRecordEntity
import com.example.data.local.SaleEntity
import com.example.data.local.ShopProfileEntity
import com.example.data.local.StockMovementEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ShopProfileRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val shopName: String = "",
    val phone: String? = null,
    val language: String = "rw",
    val monthlyReceiptLimit: Int = 60,
    val receiptsUsedThisMonth: Int = 0,
    val receiptMonthKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProductRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val unit: String = "piece",
    val buyingPrice: Long = 0L,
    val normalPrice: Long = 0L,
    val minStockLevel: Int = 5,
    val cachedStock: Int = 0,
    val supplierName: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StockMovementRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val type: String = "bought",
    val quantityDelta: Int = 0,
    val unitCost: Long = 0L,
    val reason: String? = null,
    val relatedId: String? = null,
    val dateKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SaleRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Int = 1,
    val unitPrice: Long = 0L,
    val buyingPriceAtSale: Long = 0L,
    val totalAmount: Long = 0L,
    val profitAmount: Long = 0L,
    val paymentMethod: String = "cash",
    val status: String = "completed",
    val customerName: String? = null,
    val dateKey: String = "",
    val monthKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DebtRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val direction: String = "owed_to_me",
    val partyName: String = "",
    val partyPhone: String? = null,
    val description: String? = null,
    val originalAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val status: String = "open",
    val dueDateKey: String? = null,
    val relatedSaleId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DebtPaymentRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val debtId: String = "",
    val amount: Long = 0L,
    val method: String = "cash",
    val dateKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReceiptRecordRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val supplierName: String? = null,
    val itemCount: Int = 0,
    val totalAmount: Long = 0L,
    val paymentMethod: String = "cash",
    val monthKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun ShopProfileEntity.toFirestoreMap(uid: String, isCreate: Boolean): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "shopName" to shopName.take(80).ifBlank { "SmartShelf" },
        "language" to language.ifBlank { "rw" },
        "monthlyReceiptLimit" to monthlyReceiptLimit.coerceIn(1, 1000),
        "receiptsUsedThisMonth" to receiptsUsedThisMonth.coerceIn(0, 10000),
        "receiptMonthKey" to receiptMonthKey.take(7),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    if (phone.isNotBlank()) map["phone"] = phone.take(30)
    if (isCreate) {
        map["userId"] = uid
        map["createdAt"] = FieldValue.serverTimestamp()
    }
    return map
}

fun ProductEntity.toFirestoreMap(uid: String, isCreate: Boolean): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "name" to name.take(120).ifBlank { "Item" },
        "unit" to unit,
        "buyingPrice" to buyingPrice.coerceAtLeast(0L),
        "normalPrice" to normalPrice.coerceAtLeast(0L),
        "minStockLevel" to minStockLevel.coerceAtLeast(0),
        "cachedStock" to cachedStock,
        "active" to active,
        "updatedAt" to FieldValue.serverTimestamp()
    )
    if (supplierName.isNotBlank()) map["supplierName"] = supplierName.take(120)
    if (isCreate) {
        map["userId"] = uid
        map["createdAt"] = FieldValue.serverTimestamp()
    }
    return map
}

fun StockMovementEntity.toFirestoreCreateMap(uid: String): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to uid,
        "productId" to productId,
        "type" to type,
        "quantityDelta" to quantityDelta,
        "unitCost" to unitCost.coerceAtLeast(0L),
        "dateKey" to dateKey.take(10),
        "createdAt" to FieldValue.serverTimestamp()
    )
    if (reason.isNotBlank()) map["reason"] = reason.take(120)
    if (relatedId.isNotBlank()) map["relatedId"] = relatedId.take(128)
    return map
}

fun SaleEntity.toFirestoreCreateMap(uid: String): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to uid,
        "productId" to productId,
        "productName" to productName.take(120),
        "quantity" to quantity.coerceAtLeast(1),
        "unitPrice" to unitPrice.coerceAtLeast(0L),
        "buyingPriceAtSale" to buyingPriceAtSale.coerceAtLeast(0L),
        "totalAmount" to totalAmount.coerceAtLeast(0L),
        "profitAmount" to profitAmount,
        "paymentMethod" to paymentMethod,
        "status" to status,
        "dateKey" to dateKey.take(10),
        "monthKey" to monthKey.take(7),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    if (customerName.isNotBlank()) map["customerName"] = customerName.take(120)
    return map
}

fun DebtEntity.toFirestoreMap(uid: String, isCreate: Boolean): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "paidAmount" to paidAmount.coerceIn(0L, originalAmount),
        "status" to status,
        "updatedAt" to FieldValue.serverTimestamp()
    )
    if (partyPhone.isNotBlank()) map["partyPhone"] = partyPhone.take(30)
    if (description.isNotBlank()) map["description"] = description.take(240)
    if (dueDateKey.isNotBlank()) map["dueDateKey"] = dueDateKey.take(10)
    if (isCreate) {
        map["userId"] = uid
        map["direction"] = direction
        map["partyName"] = partyName.take(120).ifBlank { "Customer" }
        map["originalAmount"] = originalAmount.coerceAtLeast(0L)
        if (relatedSaleId.isNotBlank()) map["relatedSaleId"] = relatedSaleId.take(128)
        map["createdAt"] = FieldValue.serverTimestamp()
    }
    return map
}

fun DebtPaymentEntity.toFirestoreCreateMap(uid: String): Map<String, Any> = mapOf(
    "userId" to uid,
    "debtId" to debtId,
    "amount" to amount.coerceAtLeast(1L),
    "method" to method,
    "dateKey" to dateKey.take(10),
    "createdAt" to FieldValue.serverTimestamp()
)

fun ReceiptRecordEntity.toFirestoreCreateMap(uid: String): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to uid,
        "itemCount" to itemCount.coerceAtLeast(0),
        "totalAmount" to totalAmount.coerceAtLeast(0L),
        "paymentMethod" to paymentMethod,
        "monthKey" to monthKey.take(7),
        "createdAt" to FieldValue.serverTimestamp()
    )
    if (supplierName.isNotBlank()) map["supplierName"] = supplierName.take(120)
    return map
}
