package com.bolohisab.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomerEntity::class, EntryEntity::class, EntryItemEntity::class,
        EntryHistoryEntity::class, ProductEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        const val NAME = "ledger.db"

        /** Adds entry_history for edit auditing. Existing tables are untouched — a shopkeeper's ledger must never be wiped by an app update. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entry_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`entry_id` INTEGER NOT NULL, " +
                        "`changed_at` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`total_poisha` INTEGER NOT NULL, " +
                        "`paid_poisha` INTEGER NOT NULL, " +
                        "`balance_delta` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`transcript` TEXT NOT NULL, " +
                        "FOREIGN KEY(`entry_id`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_history_entry_id` ON `entry_history` (`entry_id`)")
            }
        }

        /** Adds products for stock tracking. Existing tables are untouched. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `products` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`unit` TEXT, " +
                        "`stock_qty` REAL NOT NULL, " +
                        "`low_stock_threshold` REAL, " +
                        "`created_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
            }
        }

        /** Adds address and a profile photo path to customers. Existing rows get NULL for both. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN photo_path TEXT")
            }
        }
    }
}
