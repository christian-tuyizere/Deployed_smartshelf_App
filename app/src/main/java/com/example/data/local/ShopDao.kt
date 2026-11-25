package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop_profile LIMIT 1")
    fun observeShopProfile(): Flow<ShopProfileEntity?>

    @Query("SELECT * FROM shop_profile LIMIT 1")
    suspend fun getShopProfileOnce(): ShopProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShopProfile(profile: ShopProfileEntity)

    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Query("SELECT * FROM stock_movements ORDER BY createdAtMillis DESC")
    fun observeMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements")
    suspend fun getAllMovementsOnce(): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM sales ORDER BY createdAtMillis DESC")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales")
    suspend fun getAllSalesOnce(): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: String): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSales(sales: List<SaleEntity>)

    @Query("SELECT * FROM debts ORDER BY updatedAtMillis DESC")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts")
    suspend fun getAllDebtsOnce(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE relatedSaleId = :saleId LIMIT 1")
    suspend fun getDebtBySaleId(saleId: String): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebts(debts: List<DebtEntity>)

    @Query("SELECT * FROM debt_payments ORDER BY createdAtMillis DESC")
    fun observeDebtPayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments")
    suspend fun getAllDebtPaymentsOnce(): List<DebtPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayments(payments: List<DebtPaymentEntity>)

    @Query("SELECT * FROM receipts ORDER BY createdAtMillis DESC")
    fun observeReceipts(): Flow<List<ReceiptRecordEntity>>

    @Query("SELECT * FROM receipts")
    suspend fun getAllReceiptsOnce(): List<ReceiptRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(receipts: List<ReceiptRecordEntity>)

    @Query("SELECT * FROM queued_receipts ORDER BY createdAtMillis DESC")
    fun observeQueuedReceipts(): Flow<List<QueuedReceiptEntity>>

    @Query("SELECT * FROM queued_receipts WHERE status = 'queued'")
    suspend fun getQueuedReceiptsOnce(): List<QueuedReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueuedReceipt(queued: QueuedReceiptEntity)

    @Query("DELETE FROM queued_receipts WHERE id = :id")
    suspend fun deleteQueuedReceipt(id: String)

    @Transaction
    suspend fun recordSaleTransaction(
        sale: SaleEntity,
        movement: StockMovementEntity,
        updatedProduct: ProductEntity,
        creditDebt: DebtEntity?
    ) {
        upsertSale(sale)
        insertMovement(movement)
        upsertProduct(updatedProduct)
        if (creditDebt != null) {
            upsertDebt(creditDebt)
        }
    }

    @Transaction
    suspend fun cancelSaleTransaction(
        updatedSale: SaleEntity,
        reverseMovement: StockMovementEntity,
        updatedProduct: ProductEntity,
        cancelledDebt: DebtEntity?
    ) {
        upsertSale(updatedSale)
        insertMovement(reverseMovement)
        upsertProduct(updatedProduct)
        if (cancelledDebt != null) {
            upsertDebt(cancelledDebt)
        }
    }

    @Transaction
    suspend fun restockTransaction(
        updatedProduct: ProductEntity,
        movement: StockMovementEntity,
        supplierDebt: DebtEntity?
    ) {
        upsertProduct(updatedProduct)
        insertMovement(movement)
        if (supplierDebt != null) {
            upsertDebt(supplierDebt)
        }
    }

    @Transaction
    suspend fun adjustStockTransaction(
        updatedProduct: ProductEntity,
        movement: StockMovementEntity
    ) {
        upsertProduct(updatedProduct)
        insertMovement(movement)
    }

    @Transaction
    suspend fun recordDebtPaymentTransaction(
        updatedDebt: DebtEntity,
        payment: DebtPaymentEntity
    ) {
        upsertDebt(updatedDebt)
        insertDebtPayment(payment)
    }

    @Transaction
    suspend fun deleteAllShopData() {
        clearProducts()
        clearMovements()
        clearSales()
        clearDebts()
        clearDebtPayments()
        clearReceipts()
        clearQueuedReceipts()
        clearShopProfile()
    }

    @Query("DELETE FROM shop_profile")
    suspend fun clearShopProfile()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM stock_movements")
    suspend fun clearMovements()

    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM debts")
    suspend fun clearDebts()

    @Query("DELETE FROM debt_payments")
    suspend fun clearDebtPayments()

    @Query("DELETE FROM receipts")
    suspend fun clearReceipts()

    @Query("DELETE FROM queued_receipts")
    suspend fun clearQueuedReceipts()
}
