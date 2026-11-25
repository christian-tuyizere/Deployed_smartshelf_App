package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShopProfileEntity::class,
        ProductEntity::class,
        StockMovementEntity::class,
        SaleEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        ReceiptRecordEntity::class,
        QueuedReceiptEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE receipts ADD COLUMN itemsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clear previously auto-formulated 1.25x prices on unsold products so user sets them explicitly
                db.execSQL(
                    """
                    UPDATE products
                    SET normalPrice = 0,
                        syncPending = 1,
                        updatedAtMillis = CAST(strftime('%s','now') AS INTEGER) * 1000
                    WHERE buyingPrice > 0
                      AND normalPrice = (buyingPrice * 125) / 100
                      AND id NOT IN (SELECT DISTINCT productId FROM sales WHERE status = 'completed')
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Convert packaged container products (e.g. 4L paint tins, 1L cans, 500ML bottles) from 'liter' to 'piece'
                db.execSQL(
                    """
                    UPDATE products
                    SET unit = 'piece',
                        syncPending = 1,
                        updatedAtMillis = CAST(strftime('%s','now') AS INTEGER) * 1000
                    WHERE unit = 'liter'
                      AND (
                        upper(name) LIKE '%1L%' OR
                        upper(name) LIKE '%2L%' OR
                        upper(name) LIKE '%3L%' OR
                        upper(name) LIKE '%4L%' OR
                        upper(name) LIKE '%5L%' OR
                        upper(name) LIKE '%10L%' OR
                        upper(name) LIKE '%20L%' OR
                        upper(name) LIKE '%25L%' OR
                        upper(name) LIKE '%ML%'
                      )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartshelf_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
