package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_profile")
data class ShopProfileEntity(
    @PrimaryKey val id: String = "local_shop",
    val shopName: String = "",
    val phone: String = "",
    val language: String = "rw",
    val monthlyReceiptLimit: Int = 60,
    val receiptsUsedThisMonth: Int = 0,
    val receiptMonthKey: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String = "piece",
    val buyingPrice: Long = 0L,
    val normalPrice: Long = 0L,
    val minStockLevel: Int = 5,
    val cachedStock: Int = 0,
    val supplierName: String = "",
    val active: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val type: String, // bought, sold, adjusted, cancelled
    val quantityDelta: Int,
    val unitCost: Long,
    val reason: String = "",
    val relatedId: String = "",
    val dateKey: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val buyingPriceAtSale: Long,
    val totalAmount: Long,
    val profitAmount: Long,
    val paymentMethod: String, // cash, momo, credit
    val status: String = "completed", // completed, cancelled
    val customerName: String = "",
    val dateKey: String,
    val monthKey: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: String,
    val direction: String, // owed_to_me, i_owe
    val partyName: String,
    val partyPhone: String = "",
    val description: String = "",
    val originalAmount: Long,
    val paidAmount: Long = 0L,
    val status: String = "open", // open, paid, cancelled
    val dueDateKey: String = "",
    val relatedSaleId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "debt_payments")
data class DebtPaymentEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val amount: Long,
    val method: String, // cash, momo
    val dateKey: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "receipts")
data class ReceiptRecordEntity(
    @PrimaryKey val id: String,
    val supplierName: String = "",
    val itemCount: Int,
    val totalAmount: Long,
    val paymentMethod: String, // cash, momo, credit
    val monthKey: String,
    val imagePath: String = "",
    val itemsJson: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncPending: Boolean = true
)

@Entity(tableName = "queued_receipts")
data class QueuedReceiptEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val status: String = "queued", // queued, parsing, ready, failed
    val parsedJson: String = "",
    val errorMessage: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)
