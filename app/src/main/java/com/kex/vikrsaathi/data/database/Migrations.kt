package com.kex.vikrsaathi.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isDefault INTEGER NOT NULL,
                pageWidthPt INTEGER NOT NULL,
                pageHeightPt INTEGER NOT NULL,
                marginLeft REAL NOT NULL,
                marginTop REAL NOT NULL,
                marginRight REAL NOT NULL,
                marginBottom REAL NOT NULL,
                elementsJson TEXT NOT NULL,
                version INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_template_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                templateId INTEGER NOT NULL,
                versionNumber INTEGER NOT NULL,
                snapshotJson TEXT NOT NULL,
                savedAt INTEGER NOT NULL,
                FOREIGN KEY(templateId) REFERENCES invoice_templates(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_invoice_template_versions_templateId ON invoice_template_versions(templateId)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE bills ADD COLUMN invoiceCounter INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            """
            UPDATE bills SET invoiceCounter = CAST(billNumber AS INTEGER)
            WHERE billNumber GLOB '[0-9]*'
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_invoiceCounter ON bills(invoiceCounter)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_drafts (
                id INTEGER PRIMARY KEY NOT NULL,
                customerId INTEGER,
                customerName TEXT NOT NULL,
                buyerAddress TEXT NOT NULL,
                buyerPhone TEXT NOT NULL,
                lineItemsJson TEXT NOT NULL,
                grandTotal REAL NOT NULL,
                itemCount INTEGER NOT NULL,
                heldAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_drafts_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER,
                customerName TEXT NOT NULL,
                buyerAddress TEXT NOT NULL,
                buyerPhone TEXT NOT NULL,
                lineItemsJson TEXT NOT NULL,
                grandTotal REAL NOT NULL,
                itemCount INTEGER NOT NULL,
                heldAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_drafts_new (
                customerId, customerName, buyerAddress, buyerPhone,
                lineItemsJson, grandTotal, itemCount, heldAt
            )
            SELECT customerId, customerName, buyerAddress, buyerPhone,
                lineItemsJson, grandTotal, itemCount, heldAt
            FROM bill_drafts
            """.trimIndent()
        )
        db.execSQL("DROP TABLE bill_drafts")
        db.execSQL("ALTER TABLE bill_drafts_new RENAME TO bill_drafts")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE items ADD COLUMN stockQty INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                delta INTEGER NOT NULL,
                quantityAfter INTEGER NOT NULL,
                type TEXT NOT NULL,
                referenceType TEXT,
                referenceId INTEGER,
                note TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stock_movements_itemId_createdAt ON stock_movements(itemId, createdAt)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stock_movements_referenceType_referenceId ON stock_movements(referenceType, referenceId)"
        )
    }
}
